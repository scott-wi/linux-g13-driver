#ifndef G13_CHORD_ACTION_H
#define G13_CHORD_ACTION_H
#include "G13Action.h"
#include "Output.h"
#include <linux/input.h>
#include <vector>
#include <utility>

// A keystroke with modifiers stays held for the physical button's lifetime.
class ChordAction : public G13Action {
    std::vector<int> codes;
    void key_down() override {
        for (int code : codes) UInput::send_event(EV_KEY, code, 1);
        UInput::send_event(EV_SYN, SYN_REPORT, 0);
    }
    void key_up() override {
        for (auto i = codes.rbegin(); i != codes.rend(); ++i) UInput::send_event(EV_KEY, *i, 0);
        UInput::send_event(EV_SYN, SYN_REPORT, 0);
    }
public:
    explicit ChordAction(std::vector<int> keys) : codes(std::move(keys)) {}
    ~ChordAction() override { set(0); }
};
#endif
