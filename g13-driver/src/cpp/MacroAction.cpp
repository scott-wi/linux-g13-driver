#include <string.h>
#include <vector>
#include <memory>
#include <sstream>
#include <thread>
#include <set>
#include <algorithm>
#include <syslog.h> // Logging

#include "MacroAction.h"

/**
 * @brief The main loop for macro execution.
 */
void MacroAction::execute_macro_loop() {
    std::set<int> held;
    int iterations = 0;
    do {
        for (const auto& event : _events) {
            if (_should_stop) break;
            if (event->delay() > 0) {
                // Keep cancellation responsive even during a long imported delay.
                for (int remaining = event->delay(); remaining > 0 && !_should_stop; remaining -= 5)
                    std::this_thread::sleep_for(std::chrono::milliseconds(std::min(remaining, 5)));
            } else {
                event->execute();
                if (event->key() >= 0) {
                    if (event->down()) held.insert(event->key());
                    else held.erase(event->key());
                }
            }
        }
        ++iterations;
        if (_repeats == 0 || (_repeats > 1 && iterations >= _repeats)) break;
        std::this_thread::sleep_for(std::chrono::milliseconds(1));
    } while (!_should_stop);
    for (int code : held) UInput::send_event(EV_KEY, code, 0);
    if (!held.empty()) UInput::send_event(EV_SYN, SYN_REPORT, 0);
    _is_macro_running = false;
}

std::unique_ptr<MacroAction::Event> MacroAction::tokenToEvent(const std::string& token) {
    if (token.empty()) return nullptr;

    try {
        if (token.rfind("kd.", 0) == 0) {
            return std::make_unique<KeyDownEvent>(std::stoi(token.substr(3)));
        }
        if (token.rfind("ku.", 0) == 0) {
            return std::make_unique<KeyUpEvent>(std::stoi(token.substr(3)));
        }
        if (token.rfind("d.", 0) == 0) {
            return std::make_unique<WaitEvent>(std::stoi(token.substr(2)));
        }
    } catch (...) {
        syslog(LOG_ERR, "MacroAction::tokenToEvent: Error parsing token: %s", token.c_str());
    }
    return nullptr;
}

MacroAction::MacroAction(const std::string& sequence)
    : _repeats(0), _is_macro_running(false), _should_stop(false) {

    std::stringstream ss(sequence);
    std::string token;
    while (std::getline(ss, token, ',')) {
        if (!token.empty()) {
            auto event = tokenToEvent(token);
            if (event) _events.push_back(std::move(event));
        }
    }
}

MacroAction::~MacroAction() {
    // RAII: Ensure thread is stopped and joined before destruction
    _should_stop = true;
    if (_macro_thread.joinable()) {
        _macro_thread.join();
    }
}

void MacroAction::key_down() {
    if (isPressed()) {
        if (_is_macro_running) {
            // Toggle behavior: Stop if running
            _should_stop = true;
            if (_macro_thread.joinable()) {
                _macro_thread.join();
            }
            return;
        }

        if (_events.empty()) return;


        // Clean up previous thread if necessary (should be handled, but safe-guard)
        if (_macro_thread.joinable()) {
            _macro_thread.join();
        }

        // Reset before spawning: destruction/key-up must not race a worker reset.
        _should_stop = false;
        _is_macro_running = true;
        _macro_thread = std::thread(&MacroAction::execute_macro_loop, this);
    }
}

void MacroAction::key_up() {
    if (_repeats == 1) {
       _should_stop = true;
    }
}

int MacroAction::getRepeats() const {
    return _repeats;
}

void MacroAction::setRepeats(int repeats) {
    this->_repeats = repeats;
}