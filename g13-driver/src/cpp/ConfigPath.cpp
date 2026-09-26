#include "ConfigPath.h"
#include <cstdlib>
#include <sys/stat.h>
#include <unistd.h>
#include <pwd.h>
#include <iostream>
#include <limits.h>
#include <fstream>
#include <regex>
#include <filesystem>
#include <algorithm>
#include <cctype>
#include <set>
#include <vector>

// Helper to check if a directory exists
static bool dirExists(const std::string& path) {
    struct stat info;
    if (stat(path.c_str(), &info) != 0) {
        return false;
    }
    return (info.st_mode & S_IFDIR) != 0;
}

std::string ConfigPath::getConfigDir() {
    // 1. Try XDG_CONFIG_HOME
    const char* xdgConfig = getenv("XDG_CONFIG_HOME");
    std::string baseDir;

    if (xdgConfig && *xdgConfig) {
        baseDir = std::string(xdgConfig);
    } else {
        // 2. Fallback to HOME/.config
        const char* home = getenv("HOME");
        if (!home) {
            // Fallback for safety if HOME is unset (unlikely for user apps)
            struct passwd* pw = getpwuid(getuid());
            if (pw) {
                home = pw->pw_dir;
            } else {
                return "/tmp/g13-fallback"; 
            }
        }
        baseDir = std::string(home) + "/.config";
    }

    return baseDir + "/g13";
}

void ConfigPath::ensureConfigDirExists() {
    std::string path = getConfigDir();
    if (!dirExists(path)) {
        // Create directory with 0755 permissions
        // Note: mkdir only creates the last level, implies ~/.config exists. 
        // For robustness, a recursive mkdir would be better, but this suffices for standard systems.
        mkdir(path.c_str(), 0755);
    }
}

std::string ConfigPath::getBindingPath(int bindingId) {
    ensureConfigDirExists();
    return getConfigDir() + "/bindings-" + std::to_string(bindingId) + ".properties";
}

std::string ConfigPath::getMacroPath(int macroId) {
    ensureConfigDirExists();
    return getConfigDir() + "/macro-" + std::to_string(macroId) + ".properties";
}

std::string ConfigPath::getFifoPath() {
    // Ideally use XDG_RUNTIME_DIR for pipes (/run/user/1000/)
    const char* xdgRuntime = getenv("XDG_RUNTIME_DIR");
    if (xdgRuntime && *xdgRuntime) {
        return std::string(xdgRuntime) + "/g13-lcd";
    }
    // Fallback to tmp
    return "/tmp/g13-lcd";
}
namespace {
const std::regex uuid("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
const std::regex application("[A-Za-z0-9._+ -]{1,128}");

struct ProfileRule {
    std::string id;
    std::string name;
    std::vector<std::string> applications;
};

bool complete_profile(const std::string& directory) {
    if (!std::filesystem::is_regular_file(directory + "/profile.properties")) return false;
    for (int bank = 0; bank < 4; ++bank)
        if (!std::filesystem::is_regular_file(directory + "/bindings-" + std::to_string(bank) + ".properties")) return false;
    return true;
}

std::string profile_directory(const std::string& root, const std::string& id) {
    if (id == "default") return root;
    if (!std::regex_match(id, uuid)) return "";
    std::string directory = root + "/profiles/" + id;
    return complete_profile(directory) ? directory : "";
}

std::string read_marker(const std::string& root, const std::string& name) {
    std::ifstream marker(root + "/" + name);
    std::string id;
    std::getline(marker, id);
    return profile_directory(root, id).empty() ? "" : id;
}

ProfileRule read_profile(const std::string& id, const std::string& directory) {
    ProfileRule profile{id, id, {}};
    std::ifstream metadata(directory + "/profile.properties");
    std::string line;
    while (std::getline(metadata, line)) {
        size_t separator = line.find('=');
        if (separator == std::string::npos || line.empty() || line[0] == '#') continue;
        std::string key = line.substr(0, separator);
        std::string value = line.substr(separator + 1);
        if (key == "name") profile.name = value;
        else if (key.rfind("application.", 0) == 0 && std::regex_match(value, application))
            profile.applications.push_back(value);
    }
    return profile;
}

std::string basename(std::string value) {
    std::replace(value.begin(), value.end(), '\\', '/');
    size_t separator = value.find_last_of('/');
    return separator == std::string::npos ? value : value.substr(separator + 1);
}

std::set<std::string> running_applications() {
    std::set<std::string> result;
    const char* override_root = getenv("G13_PROC_ROOT");
    std::filesystem::path proc = override_root && *override_root ? override_root : "/proc";
    std::error_code error;
    for (const auto& entry : std::filesystem::directory_iterator(proc,
            std::filesystem::directory_options::skip_permission_denied, error)) {
        if (error) break;
        std::string pid = entry.path().filename().string();
        if (pid.empty() || !std::all_of(pid.begin(), pid.end(), [](unsigned char value) { return std::isdigit(value); })) continue;
        std::filesystem::path executable = std::filesystem::read_symlink(entry.path() / "exe", error);
        if (!error) result.insert(executable.filename().string());
        error.clear();
        std::ifstream command(entry.path() / "cmdline", std::ios::binary);
        std::string token;
        while (std::getline(command, token, '\0')) {
            token = basename(token);
            if (std::regex_match(token, application)) result.insert(token);
        }
    }
    return result;
}
}

std::string ConfigPath::getSelectedProfileId() {
    std::string root = getConfigDir();
    std::string persistent = read_marker(root, "persistent-profile");
    if (!persistent.empty()) return persistent;

    std::vector<ProfileRule> profiles;
    std::filesystem::path profile_root = root + "/profiles";
    std::error_code error;
    for (const auto& entry : std::filesystem::directory_iterator(profile_root,
            std::filesystem::directory_options::skip_permission_denied, error)) {
        if (error) break;
        std::string id = entry.path().filename().string();
        if (std::regex_match(id, uuid) && complete_profile(entry.path().string()))
            profiles.push_back(read_profile(id, entry.path().string()));
    }
    std::sort(profiles.begin(), profiles.end(), [](const ProfileRule& left, const ProfileRule& right) {
        return left.name < right.name || (left.name == right.name && left.id < right.id);
    });
    const std::set<std::string> running = running_applications();
    for (const auto& profile : profiles)
        for (const auto& executable : profile.applications)
            if (running.count(executable)) return profile.id;

    std::string fallback = read_marker(root, "default-profile");
    return fallback.empty() ? "default" : fallback;
}

std::string ConfigPath::getActiveProfileDir() {
    std::string root = getConfigDir();
    std::string selected = getSelectedProfileId();
    std::string directory = profile_directory(root, selected);
    return directory.empty() ? root : directory;
}
