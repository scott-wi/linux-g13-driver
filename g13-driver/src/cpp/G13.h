#ifndef __G13_H__
#define __G13_H__

#include <string>
#include <vector>
#include <memory>
#include <map>
#include <istream>
#include <libusb-1.0/libusb.h>
#include <time.h> // For time_t

#include "Constants.h"
#include "G13Action.h"
#include "Macro.h"

class G13 {
private:
    std::vector<std::unique_ptr<G13Action>> actions;

    libusb_device        *device;       
    libusb_device_handle *handle;        
    int                   uinput_file;   

    int                   loaded;        
    volatile int          keepGoing;     

    stick_mode_t          stick_mode;    
    int                   stick_keys[4];   
    int                   bindings;      
    int                   bank_targets[G13_NUM_KEYS];
    bool                  bank_switch_held[G13_NUM_KEYS];

    unsigned char lcd_buffer[G13_LCD_BUFFER_SIZE];

    // Feature: Live-Reload
    time_t last_config_mtime;
    long last_config_nsec = 0;
    time_t last_profile_scan = 0;
    std::string profile_directory;
    std::string active_profile_id = "default";
    std::string layout_event;
    bool hardware_pressed[G13_NUM_KEYS] = {};
    long long press_events[G13_NUM_KEYS] = {};
    bool input_state_dirty = false;
    void record_key_state(int key, bool pressed);
    void check_for_config_update();
    void publish_state();

    // --- Private Methods ---
    std::unique_ptr<Macro> loadMacro(int id);
    void parse_bindings_from_stream(std::istream& stream);
    int  read();
    void parse_joystick(unsigned char *buf);
    void handle_key_state(int key, int pressed);
    void parse_key(int key, unsigned char *byte);
    void parse_keys(unsigned char *buf);

    // FIFO / Pipe for external input
    int fifo_fd = -1;             // File Descriptor for the pipe
    std::string fifo_path;   // Path to pipe (default: /tmp/g13-lcd)
    
    void init_fifo();        // Create pipe
    void check_fifo();       // Read pipe data
    void cleanup_fifo();     // Remove pipe


public:
    G13(libusb_device *device);
    ~G13();

    void start();
    void stop();
    void loadBindings();
    void setColor(int r, int g, int b);

    // --- LCD ---
    void clear_lcd_buffer();
    void set_pixel(int x, int y, bool on);
    void write_lcd();
    void draw_test_pattern();
    void write_char(int x, int y, char c);
    void write_text(int x, int y, const std::string& text);
};

#endif
