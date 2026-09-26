package com.booker.g13;

import java.awt.Component;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.plaf.ComponentUI;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;
import javax.swing.plaf.metal.MetalComboBoxUI;

/** Shared Metal dropdown behavior: finish the mouse click before opening the popup. */
public final class ClickComboBoxUI extends MetalComboBoxUI {
    public static ComponentUI createUI(JComponent component) {
        return new ClickComboBoxUI();
    }

    @Override protected ComboPopup createPopup() {
        return new BasicComboPopup(comboBox) {
            @Override protected MouseListener createMouseListener() {
                return opener(comboBox);
            }
        };
    }

    static MouseListener opener(JComboBox<?> combo) {
        return new MouseAdapter() {
            private Component pressedOn;

            @Override public void mousePressed(MouseEvent event) {
                pressedOn = null;
                if (SwingUtilities.isLeftMouseButton(event) && combo.isEnabled()) {
                    pressedOn = event.getComponent();
                    Component focus = combo.isEditable() ? combo.getEditor().getEditorComponent() : combo;
                    if (!(focus instanceof JComponent target) || target.isRequestFocusEnabled())
                        focus.requestFocusInWindow();
                }
            }

            @Override public void mouseClicked(MouseEvent event) {
                boolean clicked = event.getComponent() == pressedOn;
                pressedOn = null;
                if (clicked && SwingUtilities.isLeftMouseButton(event) && event.getClickCount() == 1
                        && combo.isEnabled() && event.getComponent().contains(event.getPoint())) {
                    combo.setPopupVisible(!combo.isPopupVisible());
                }
            }
        };
    }
}
