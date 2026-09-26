#include "ConfigPath.h"
#include "MacroAction.h"
#include "ChordAction.h"
#include <filesystem>
#include <fstream>
#include <cassert>
#include <mutex>
#include <tuple>
#include <iostream>
#include <map>
#include <csignal>
#include <libusb-1.0/libusb.h>
#define private public
#include "G13.h"
#undef private
volatile sig_atomic_t daemon_keep_running = 1;
// No device is opened; control transfers are captured as no-ops.
extern "C" int libusb_open(libusb_device*, libusb_device_handle**) { return LIBUSB_ERROR_NO_DEVICE; }
extern "C" int libusb_control_transfer(libusb_device_handle*, uint8_t, uint8_t, uint16_t, uint16_t, unsigned char*, uint16_t, unsigned int) { return 0; }


std::mutex eventsMutex;
std::vector<std::tuple<int,int,int>> events;
void UInput::send_event(int type, int code, int value) {
    std::lock_guard<std::mutex> lock(eventsMutex);
    events.emplace_back(type, code, value);
}
static bool contains(int code, int value) {
    std::lock_guard<std::mutex> lock(eventsMutex);
    for (auto [type, c, v] : events) if (type == EV_KEY && c == code && v == value) return true;
    return false;
}
int main(int argc, char** argv) {
    assert(argc == 2);
    setenv("XDG_CONFIG_HOME", argv[1], 1);
    setenv("XDG_RUNTIME_DIR", argv[1], 1);
    std::string root = std::string(argv[1]) + "/g13";
    std::string proc = std::string(argv[1]) + "/proc";
    setenv("G13_PROC_ROOT", proc.c_str(), 1);
    std::filesystem::create_directories(root);
    std::filesystem::create_directories(proc);
    assert(ConfigPath::getActiveProfileDir() == root);
    std::string id = "12345678-1234-1234-1234-123456789abc";
    std::string dir = root + "/profiles/" + id;
    std::filesystem::create_directories(dir);
    std::ofstream(dir + "/profile.properties") << "name=Test\napplication.0=example-game\n";
    assert(ConfigPath::getActiveProfileDir() == root); // incomplete imports are never loaded
    for (int i = 0; i < 4; ++i) std::ofstream(dir + "/bindings-" + std::to_string(i) + ".properties") << "color=255,255,255\n";
    std::ofstream(root + "/default-profile") << id << '\n';
    assert(ConfigPath::getActiveProfileDir() == dir);
    std::ofstream(root + "/default-profile") << "default\n";
    assert(ConfigPath::getActiveProfileDir() == root);
    std::filesystem::create_directories(proc + "/123");
    {
        std::ofstream command(proc + "/123/cmdline", std::ios::binary);
        const std::string value = "/games/example-game";
        command.write(value.c_str(), value.size() + 1);
    }
    assert(ConfigPath::getSelectedProfileId() == id); // running application wins
    std::string alphabetical_id = "00000000-0000-0000-0000-000000000001";
    std::string alphabetical_dir = root + "/profiles/" + alphabetical_id;
    std::filesystem::create_directories(alphabetical_dir);
    std::ofstream(alphabetical_dir + "/profile.properties") << "name=Aardvark\napplication.0=example-game\n";
    for (int i = 0; i < 4; ++i)
        std::ofstream(alphabetical_dir + "/bindings-" + std::to_string(i) + ".properties") << "color=255,255,255\n";
    assert(ConfigPath::getSelectedProfileId() == alphabetical_id); // deterministic priority when both match
    std::filesystem::remove_all(proc + "/123");
    std::ofstream(alphabetical_dir + "/profile.properties") << "name=Aardvark\napplication.0=native-game\n";
    std::filesystem::create_directories(proc + "/124");
    std::filesystem::create_symlink("/games/native-game", proc + "/124/exe");
    assert(ConfigPath::getSelectedProfileId() == alphabetical_id); // native executable link is detected
    std::filesystem::remove_all(proc + "/124");
    std::filesystem::remove_all(alphabetical_dir);
    std::filesystem::create_directories(proc + "/123");
    {
        std::ofstream command(proc + "/123/cmdline", std::ios::binary);
        const std::string value = "/games/example-game";
        command.write(value.c_str(), value.size() + 1);
    }
    std::ofstream(root + "/persistent-profile") << "default\n";
    assert(ConfigPath::getActiveProfileDir() == root); // persistence overrides a match
    std::ofstream(root + "/persistent-profile") << id << '\n';
    assert(ConfigPath::getActiveProfileDir() == dir);
    std::filesystem::remove(root + "/persistent-profile");
    std::filesystem::remove_all(proc + "/123");
    assert(ConfigPath::getActiveProfileDir() == root); // configured default fallback
    std::ofstream(root + "/persistent-profile") << id << '\n';
    {
        G13 device(nullptr);
        std::ofstream(dir + "/bindings-0.properties") << "format=2\nG0=p,k.31\nG30=b,1\n";
        device.loadBindings();
        assert(std::filesystem::exists(std::string(argv[1]) + "/g13-state.properties"));
        {
            std::ifstream state(std::string(argv[1]) + "/g13-state.properties");
            std::string contents((std::istreambuf_iterator<char>(state)), std::istreambuf_iterator<char>());
            assert(contents.find("profile=" + id) != std::string::npos && contents.find("layout=0") != std::string::npos);
        }
        device.actions[0]->set(1);
        assert(contains(31, 1));
        unsigned char report[5] = {};
        report[G13_KEY_M2 / 8] = 1 << (G13_KEY_M2 % 8);
        device.parse_key(G13_KEY_M2, report);
        assert(device.bindings == 1);
        {
            std::ifstream state(std::string(argv[1]) + "/g13-state.properties");
            std::string contents((std::istreambuf_iterator<char>(state)), std::istreambuf_iterator<char>());
            assert(contents.find("layout=1") != std::string::npos);
            assert(!device.layout_event.empty());
            assert(contents.find("layout-event=" + device.layout_event) != std::string::npos);
        }
        const std::string first_layout_event = device.layout_event;
        device.parse_key(G13_KEY_M2, report); // held report must not publish another press
        assert(device.layout_event == first_layout_event);
        assert(contains(31, 0));
        unsigned char released[5] = {};
        device.parse_key(G13_KEY_M2, released);
        device.parse_key(G13_KEY_M2, report); // reselecting M2 still notifies the GUI
        assert(device.bindings == 1 && device.layout_event != first_layout_event);
        {
            std::ifstream state(std::string(argv[1]) + "/g13-state.properties");
            std::string contents((std::istreambuf_iterator<char>(state)), std::istreambuf_iterator<char>());
            assert(contents.find("layout-event=" + device.layout_event) != std::string::npos);
        }
        const std::string reselected_event = device.layout_event;
        device.loadBindings(); // ordinary config reload must not look like a hardware press
        assert(device.layout_event == reselected_event);
        device.parse_key(G13_KEY_M2, released);
        unsigned char m1[5] = {};
        m1[G13_KEY_M1 / 8] = 1 << (G13_KEY_M1 % 8);
        device.parse_key(G13_KEY_M1, m1);
        assert(device.bindings == 0);
        device.parse_key(G13_KEY_M1, released);
        device.parse_key(G13_KEY_M2, report);
        assert(device.bindings == 1);
        device.parse_key(G13_KEY_M2, released);
        {
            std::lock_guard<std::mutex> lock(eventsMutex);
            events.clear();
        }
        device.actions[0]->set(1);
        assert(!contains(31, 1)); // absent entry must not survive bank change
        std::ofstream(dir + "/bindings-1.properties") << "format=2\nG30=p,k.272\nG0=b,2\n";
        device.loadBindings();
        device.parse_key(G13_KEY_M2, report);
        device.parse_key(G13_KEY_M2, released);
        assert(device.bindings == 1 && contains(272, 1) && contains(272, 0)); // M2 can be a mouse button
        unsigned char g1[5] = {};
        g1[G13_KEY_G1 / 8] = 1 << (G13_KEY_G1 % 8);
        device.parse_key(G13_KEY_G1, g1);
        assert(device.bindings == 2); // any physical key can select a layout
        std::ofstream(dir + "/bindings-2.properties") << "format=2\nG1=p,k.164\n";
        device.loadBindings();
        assert(device.stick_mode == STICK_KEYS);
        device.actions[1]->set(1);
        device.actions[1]->set(0);
        assert(contains(164, 1) && contains(164, 0)); // media key range is accepted
        std::ofstream(dir + "/bindings-2.properties") << "format=2\nstick=absolute\nG35=p,k.289\n";
        device.loadBindings();
        assert(device.stick_mode == STICK_ABSOLUTE); // imported joystick mode is applied per layout
        device.actions[35]->set(1);
        device.actions[35]->set(0);
        assert(contains(289, 1) && contains(289, 0));
        std::ofstream(root + "/bindings-2.properties") << "format=2\nG0=p,k.32\n";
        std::ofstream(root + "/persistent-profile") << "default\n";
        device.last_profile_scan = 0;
        device.check_for_config_update();
        assert(device.bindings == 2 && device.profile_directory == root);
        device.actions[0]->set(1);
        assert(contains(32, 1));
        std::ofstream(root + "/bindings-2.properties") << "format=2\nG0=p,k.33\n";
        device.check_for_config_update();
        assert(contains(32, 0));
        device.actions[0]->set(1);
        assert(contains(33, 1));
    }
    {
        ChordAction chord({42, 17});
        chord.set(1);
        assert(contains(42, 1) && contains(17, 1));
        assert(!contains(17, 0)); // hold until physical release or destruction
    }
    assert(contains(17, 0) && contains(42, 0));
    {
        MacroAction macro("kd.18,d.60000,ku.18");
        macro.set(1);
        for (int i = 0; i < 200 && !contains(18, 1); ++i) std::this_thread::sleep_for(std::chrono::milliseconds(1));
        assert(contains(18, 1));
    } // must interrupt long delay and balance pressed key
    assert(contains(18, 0));
    for (int i = 0; i < 200; ++i) {
        MacroAction macro("kd.30,d.1,ku.30");
        macro.setRepeats(1);
        macro.set(1);
    } // exercises stop-before-worker-start race
    {
        G13 device(nullptr);
        device.handle_key_state(0, 1); // even an unassigned physical key is visible
        device.handle_key_state(35, 1);
        assert(device.hardware_pressed[0] && device.hardware_pressed[35]);
        const auto stamp = device.press_events[0];
        assert(stamp > 0);
        device.publish_state();
        assert(!device.input_state_dirty);
        device.handle_key_state(0, 1);
        assert(device.press_events[0] == stamp && !device.input_state_dirty);
        device.handle_key_state(0, 0);
        assert(!device.hardware_pressed[0] && device.press_events[0] == stamp && device.input_state_dirty);
        device.publish_state();
        {
            std::ifstream state(std::string(argv[1]) + "/g13-state.properties");
            std::string contents((std::istreambuf_iterator<char>(state)), std::istreambuf_iterator<char>());
            assert(contents.find("pressed=35\n") != std::string::npos);
            assert(contents.find("press-events=" + std::to_string(stamp)) != std::string::npos);
        }
        device.stick_mode = STICK_ABSOLUTE;
        unsigned char stick[G13_REPORT_SIZE] = {};
        device.parse_joystick(stick);
        assert(device.hardware_pressed[36] && device.hardware_pressed[37]);
        stick[1] = 128; stick[2] = 128;
        device.parse_joystick(stick);
        assert(!device.hardware_pressed[36] && !device.hardware_pressed[37]);
        device.actions[1]->set(1);
        assert(!device.hardware_pressed[1]); // synthetic output does not masquerade as physical input
    }
    std::cout << "Native bank mapping, hardware input state, profile reload, chord lifetime and macro cancellation tests passed.\n";
}
