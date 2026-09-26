#include <vector>
#include <sys/stat.h>
#include <stdio.h>
#include <string.h>
#include <signal.h>
#include <stdlib.h>
#include <unistd.h>
#include <iomanip>
#include <linux/uinput.h>
#include <fcntl.h>
#include <mutex>
#include <cerrno>
#include <sys/time.h> // for gettimeofday
#include <syslog.h> //  Logging

#include "Output.h"
#include "Constants.h"

using namespace std;

// Initialization of static class members.
int UInput::keyboard_file = -1;
int UInput::pointer_file = -1;
int UInput::joystick_file = -1;
std::mutex UInput::plock;

namespace {
bool pointer_button(int code) {
    return code >= BTN_MOUSE && code <= BTN_TASK;
}

bool joystick_button(int code) {
    return (code >= BTN_MISC && code <= BTN_9)
        || (code >= BTN_JOYSTICK && code <= BTN_THUMBR)
        || (code >= BTN_DPAD_UP && code <= BTN_DPAD_RIGHT)
        || (code >= BTN_TRIGGER_HAPPY1 && code <= BTN_TRIGGER_HAPPY40);
}

bool any_button(int code) {
    return (code >= BTN_MISC && code <= BTN_GEAR_UP)
        || (code >= BTN_DPAD_UP && code <= BTN_DPAD_RIGHT)
        || (code >= BTN_TRIGGER_HAPPY1 && code <= BTN_TRIGGER_HAPPY40);
}

bool set_capability(int file, unsigned long request, int value, const char* name) {
    if (ioctl(file, request, value) == 0) return true;
    syslog(LOG_ERR, "Could not configure %s capability %d: %s", name, value, strerror(errno));
    return false;
}

bool finish_device(int file, uinput_user_dev& device, const char* name) {
    if (write(file, &device, sizeof(device)) != static_cast<ssize_t>(sizeof(device))) {
        syslog(LOG_ERR, "Could not configure %s: %s", name, strerror(errno));
        return false;
    }
    if (ioctl(file, UI_DEV_CREATE) == 0) return true;
    syslog(LOG_ERR, "Could not create %s: %s", name, strerror(errno));
    return false;
}

void close_device(int& file) {
    if (file < 0) return;
    ioctl(file, UI_DEV_DESTROY);
    close(file);
    file = -1;
}
}

/**
 * @brief Sends a single input event to the virtual uinput device.
 */
void UInput::send_event(int type, int code, int val) {
    // Modern C++ RAII lock (replaces pthread_mutex_lock/unlock)
	const std::lock_guard<std::mutex> lock(plock);

	struct input_event event;
	memset(&event, 0, sizeof(event));
	gettimeofday(&event.time, nullptr); // Set the event timestamp.
	event.type = type;
	event.code = code;
	event.value = val;

	// Write the event structure to the uinput file descriptor.
    int targets[3];
    int count = 0;
    if (type == EV_SYN) {
        targets[count++] = keyboard_file;
        targets[count++] = pointer_file;
        targets[count++] = joystick_file;
    } else if (type == EV_ABS || (type == EV_KEY && joystick_button(code))) {
        targets[count++] = joystick_file;
    } else if (type == EV_KEY && pointer_button(code)) {
        targets[count++] = pointer_file;
    } else {
        targets[count++] = keyboard_file;
    }
    for (int target : targets) {
        if (target < 0) continue;
        ssize_t written = write(target, &event, sizeof(event));
        if (written != static_cast<ssize_t>(sizeof(event)))
            syslog(LOG_ERR, "Failed to emit uinput event type=%d code=%d value=%d: %s",
                    type, code, val, written < 0 ? strerror(errno) : "short write");
    }
#ifdef G13_INPUT_DEBUG
    const char* input_debug = getenv("G13_INPUT_DEBUG");
	if (type == EV_KEY && input_debug && strcmp(input_debug, "1") == 0)
        syslog(LOG_DEBUG, "Output key code=%d value=%d", code, val);
#endif
}

/**
 * @brief Flushes any buffered data.
 */
void UInput::flush() {
	const std::lock_guard<std::mutex> lock(plock);
	if (keyboard_file >= 0) fsync(keyboard_file);
	if (pointer_file >= 0) fsync(pointer_file);
	if (joystick_file >= 0) fsync(joystick_file);
}

/**
 * @brief Closes and destroys the virtual uinput device.
 */
void UInput::close_uinput() {
    const std::lock_guard<std::mutex> lock(plock);
    close_device(keyboard_file);
    close_device(pointer_file);
    close_device(joystick_file);
}

/**
 * @brief Creates and configures the virtual uinput device.
 */
bool UInput::create_uinput() {
	const char* dev_uinput_fname =
			access("/dev/input/uinput", F_OK) == 0 ? "/dev/input/uinput" :
			access("/dev/uinput", F_OK) == 0 ? "/dev/uinput" : 0;

	if (!dev_uinput_fname) {
		syslog(LOG_ERR, "Could not find an uinput device");
		return false;
	}

	if (access(dev_uinput_fname, W_OK) != 0) {
		syslog(LOG_ERR, "%s doesn't grant write permissions", dev_uinput_fname);
		return false;
	}

    keyboard_file = open(dev_uinput_fname, O_WRONLY);
    pointer_file = open(dev_uinput_fname, O_WRONLY);
    joystick_file = open(dev_uinput_fname, O_WRONLY);
    if (keyboard_file < 0 || pointer_file < 0 || joystick_file < 0) {
        syslog(LOG_ERR, "Could not open uinput: %s", strerror(errno));
        close_uinput();
        return false;
    }

    uinput_user_dev keyboard{};
    snprintf(keyboard.name, UINPUT_MAX_NAME_SIZE, "%s", "G13 Keyboard");
    keyboard.id = {BUS_USB, G13_VENDOR_ID, G13_PRODUCT_ID, 1};
    if (!set_capability(keyboard_file, UI_SET_EVBIT, EV_KEY, keyboard.name)) { close_uinput(); return false; }
    for (int code = 1; code <= KEY_MAX; ++code)
        if (!any_button(code)
                && !set_capability(keyboard_file, UI_SET_KEYBIT, code, keyboard.name)) { close_uinput(); return false; }
    if (!finish_device(keyboard_file, keyboard, keyboard.name)) { close_uinput(); return false; }

    uinput_user_dev pointer{};
    snprintf(pointer.name, UINPUT_MAX_NAME_SIZE, "%s", "G13 Pointer");
    pointer.id = {BUS_USB, G13_VENDOR_ID, G13_PRODUCT_ID, 1};
    if (!set_capability(pointer_file, UI_SET_EVBIT, EV_KEY, pointer.name)
            || !set_capability(pointer_file, UI_SET_EVBIT, EV_REL, pointer.name)
            || !set_capability(pointer_file, UI_SET_RELBIT, REL_X, pointer.name)
            || !set_capability(pointer_file, UI_SET_RELBIT, REL_Y, pointer.name)) { close_uinput(); return false; }
    for (int code = BTN_MOUSE; code <= BTN_TASK; ++code)
        if (!set_capability(pointer_file, UI_SET_KEYBIT, code, pointer.name)) { close_uinput(); return false; }
    if (!finish_device(pointer_file, pointer, pointer.name)) { close_uinput(); return false; }

    uinput_user_dev joystick{};
    snprintf(joystick.name, UINPUT_MAX_NAME_SIZE, "%s", "G13 Joystick");
    joystick.id = {BUS_USB, G13_VENDOR_ID, G13_PRODUCT_ID, 1};
    joystick.absmin[ABS_X] = joystick.absmin[ABS_Y] = 0;
    joystick.absmax[ABS_X] = joystick.absmax[ABS_Y] = 0xff;
    if (!set_capability(joystick_file, UI_SET_EVBIT, EV_KEY, joystick.name)
            || !set_capability(joystick_file, UI_SET_EVBIT, EV_ABS, joystick.name)
            || !set_capability(joystick_file, UI_SET_ABSBIT, ABS_X, joystick.name)
            || !set_capability(joystick_file, UI_SET_ABSBIT, ABS_Y, joystick.name)) { close_uinput(); return false; }
    for (int code = 1; code <= KEY_MAX; ++code)
        if (joystick_button(code) && !set_capability(joystick_file, UI_SET_KEYBIT, code, joystick.name)) { close_uinput(); return false; }
    if (!finish_device(joystick_file, joystick, joystick.name)) { close_uinput(); return false; }
    return true;
}
