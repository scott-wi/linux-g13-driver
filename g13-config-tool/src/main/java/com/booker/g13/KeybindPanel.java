package com.booker.g13;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.Properties;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * A JPanel for configuring the binding of a single selected key.
 * It allows the user to choose between a passthrough key, a macro, or no assignment.
 */
public class KeybindPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	// --- UI Components for Passthrough Binding ---
	private final JCheckBox passthroughButton = new JCheckBox("Pass through");
	private final JTextField passthroughText = new JTextField();
	private final JButton chooseKeyButton = new JButton("Choose…");
	private final ButtonGroup buttonGroup = new ButtonGroup();
    private int passthroughCode = 0; // The Linux keycode for the passthrough key.
	
	// --- UI Components for Macro Binding ---
	private final JCheckBox macroButton = new JCheckBox("Macro");
	private final JComboBox<Properties> macroSelectionBox = new ClickComboBox<>();
	private final JCheckBox repeatsCheckBox = new JCheckBox("Auto repeat");
	private final JCheckBox bankButton = new JCheckBox("Switch layout");
	private final JComboBox<String> bankSelectionBox = new JComboBox<>(new String[]{"M1", "M2", "M3"});
	
	// --- UI Components for Screen Color ---
	private final JButton colorChangeButton = new JButton("Choose screen color");
	private final JComboBox<String> joystickMode = new JComboBox<>(new String[]{"Mapped keys", "Analog joystick"});
	
	// --- State Variables ---
	private int bindingsId = -1; // The ID of the currently loaded button layout (0-2).
	private Properties bindings; // The properties for the current binding profile.
	private Properties[] macros; // All available macros, for the dropdown list.
	private Key key = null; // The currently selected key being edited.
	
	/** A flag to prevent listeners from firing during programmatic data loading. */
	private volatile boolean loadingData = false;
	
	/**
	 * Constructs the KeybindPanel, setting up its UI and event listeners.
	 */
	public KeybindPanel() {
		setLayout(new BorderLayout(0, 10));
		setBorder(UiTheme.sectionBorder("Key bindings"));
		
		setupUI();
        attachListeners();
		
		// Initially, no key is selected, so the panel is disabled.
		setSelectedKey(null);
	}
	
	/**
	 * Creates and arranges all UI components within the panel.
	 */
	private void setupUI() {
		add(createColorPanel(), BorderLayout.NORTH);
		

		buttonGroup.add(passthroughButton);
		buttonGroup.add(macroButton);
		buttonGroup.add(bankButton);
		
		// Disable focus traversal for the passthrough text field to capture all key events.
		passthroughText.setFocusTraversalKeysEnabled(false);
		
		final JPanel grid = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = GridBagConstraints.WEST;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.insets = new Insets(0, 0, 10, 10);
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 0;
		grid.add(passthroughButton, c);
		c.gridx = 1;
		c.weightx = 1;
		c.insets = new Insets(0, 0, 10, 0);
		JPanel keyControl = new JPanel(new BorderLayout(8, 0));
		keyControl.add(passthroughText, BorderLayout.CENTER);
		keyControl.add(chooseKeyButton, BorderLayout.EAST);
		grid.add(keyControl, c);
		c.gridx = 0;
		c.gridy = 1;
		c.weightx = 0;
		c.insets = new Insets(0, 0, 8, 10);
		grid.add(macroButton, c);
		c.gridx = 1;
		c.weightx = 1;
		c.insets = new Insets(0, 0, 8, 0);
		grid.add(macroSelectionBox, c);
		c.gridy = 2;
		c.insets = new Insets(0, 0, 10, 0);
		grid.add(repeatsCheckBox, c);
		c.gridx = 0;
		c.gridy = 3;
		c.weightx = 0;
		c.insets = new Insets(0, 0, 0, 10);
		grid.add(bankButton, c);
		c.gridx = 1;
		c.weightx = 1;
		c.insets = new Insets(0, 0, 0, 0);
		grid.add(bankSelectionBox, c);
		
		// Use a custom renderer to display macro names in the combo box.
		macroSelectionBox.setRenderer(new MacroListCellRenderer());
		
		add(grid, BorderLayout.CENTER);
	}

	/**
	 * Attaches all necessary event listeners to the UI components.
	 */
	private void attachListeners() {
		// Use lambda expressions for concise listener implementation.
		macroButton.addActionListener(e -> updateComponentStateAndSave());
		macroSelectionBox.addActionListener(e -> saveBindings());
		repeatsCheckBox.addActionListener(e -> saveBindings());
		passthroughButton.addActionListener(e -> updateComponentStateAndSave());
		bankButton.addActionListener(e -> updateComponentStateAndSave());
		bankSelectionBox.addActionListener(e -> saveBindings());
		chooseKeyButton.addActionListener(e -> chooseLinuxKey());
		joystickMode.addActionListener(e -> saveJoystickMode());

		passthroughText.addKeyListener(new KeyAdapter() {
			@Override
			public void keyReleased(KeyEvent event) {
				if (loadingData || !passthroughButton.isSelected()) return;
				loadingData = true; // Prevent re-triggering while updating
				passthroughCode = JavaToLinuxKeymapping.keyEventToCCode(event);
				passthroughText.setText(JavaToLinuxKeymapping.cKeyCodeToString(passthroughCode));
				loadingData = false;
				saveBindings();
			}
		});
	}

    /**
     * A helper method to update the enabled state of components based on radio button selection,
     * and then trigger a save operation.
     */
    private void updateComponentStateAndSave() {
        boolean chord = key != null && bindings != null && bindings.getProperty("G" + key.getG13KeyCode(), "").startsWith("c,")
                && !passthroughButton.isSelected() && !macroButton.isSelected() && !bankButton.isSelected();
        passthroughText.setEnabled(passthroughButton.isSelected() || chord);
        passthroughText.setEditable(passthroughButton.isSelected());
        chooseKeyButton.setEnabled(passthroughButton.isSelected());
        macroSelectionBox.setEnabled(macroButton.isSelected());
        repeatsCheckBox.setEnabled(macroButton.isSelected());
        bankSelectionBox.setEnabled(bankButton.isSelected());
        saveBindings();
    }

	private void chooseLinuxKey() {
		JComboBox<JavaToLinuxKeymapping.KeyMapping> choices = new JComboBox<>(
				JavaToLinuxKeymapping.mappings().toArray(JavaToLinuxKeymapping.KeyMapping[]::new));
		choices.setMaximumRowCount(18);
		JavaToLinuxKeymapping.KeyMapping current = JavaToLinuxKeymapping.C_CODE_TO_DATA.get(passthroughCode);
		if (current != null) choices.setSelectedItem(current);
		if (JOptionPane.showConfirmDialog(this, choices, "Choose keyboard, media, or mouse input",
				JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
		JavaToLinuxKeymapping.KeyMapping selected = (JavaToLinuxKeymapping.KeyMapping) choices.getSelectedItem();
		if (selected == null) return;
		passthroughCode = selected.linuxCode();
		passthroughText.setText(selected.name());
		saveBindings();
	}
	
	/**
	 * Populates the macro selection combo box with the available macros.
	 * @param macros An array of Properties, where each represents a macro.
	 */
	public void setMacros(final Properties[] macros) {
		loadingData = true;
		this.macros = macros;
		
		macroSelectionBox.removeAllItems();
		for (final Properties properties : macros) {
			macroSelectionBox.addItem(properties);
		}
		
		loadingData = false;
	}
	
	/**
	 * Loads a specific binding profile into the panel.
	 * @param propertyNum The ID of the editable button layout (0-2).
	 * @param bindings The Properties object for the profile.
	 */
	public void setBindings(final int propertyNum, final Properties bindings) {
		loadingData = true;
		
		this.bindingsId = propertyNum;
		this.bindings = bindings;
		
		// Parse and set the background color from the properties.
		final String val = bindings.getProperty("color", "255,255,255");
		try {
			String[] parts = val.split(",");
			if (parts.length == 3) {
				int r = Integer.parseInt(parts[0].trim());
				int g = Integer.parseInt(parts[1].trim());
				int b = Integer.parseInt(parts[2].trim());
				setColorButton(new Color(r, g, b));
			}
		} catch (NumberFormatException e) {
			System.err.println("Invalid color format in properties: " + val);
			setColorButton(Color.WHITE); // Fallback to white.
		}
		joystickMode.setSelectedIndex("absolute".equals(bindings.getProperty("stick")) ? 1 : 0);
		
		setSelectedKey(null); // Reset selection when bindings change.
		loadingData = false;
	}
	
	/**
	 * Updates the panel's UI to reflect the configuration of the given key.
	 * This is the main method for controlling the panel's state.
	 * @param key The Key object to be edited, or null to disable the panel.
	 */
	public void setSelectedKey(final Key key) {
		this.key = key;
		loadingData = true;
		
		final boolean isKeySelected = (key != null);
		// Enable or disable all controls based on whether a key is selected.
		final JComponent[] all = { macroButton, macroSelectionBox, passthroughButton,
				passthroughText, chooseKeyButton, repeatsCheckBox, bankButton, bankSelectionBox };
		for (final JComponent c : all) {
			c.setEnabled(isKeySelected);
		}
		
		if (!isKeySelected) {
			loadingData = false;
			return;
		}
		
		// Get the binding string for the selected key, e.g., "p,k.1" or "m,5,1".
		final String propKey = "G" + key.getG13KeyCode();
		final String val = bindings.getProperty(propKey, ""); // Missing entries stay unassigned.
		
		String[] parts = val.split("[,.]");
		String type = parts.length > 0 ? parts[0] : "p";
		
		try {
			if ("p".equals(type)) { // Passthrough type
				passthroughButton.setSelected(true);
				passthroughCode = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 1; // Default keycode 1 (ESC).
				passthroughText.setText(JavaToLinuxKeymapping.cKeyCodeToString(passthroughCode));
            } else if ("c".equals(type)) {
                buttonGroup.clearSelection();
                passthroughText.setText("Chord: " + java.util.Arrays.stream(parts).skip(1)
                    .map(Integer::parseInt).map(JavaToLinuxKeymapping::cKeyCodeToString)
                    .collect(java.util.stream.Collectors.joining(" + ")));
            } else if ("m".equals(type)) { // Macro type "m"
				macroButton.setSelected(true);
				int macroNum = (parts.length >= 2) ? Integer.parseInt(parts[1]) : 0;
				macroSelectionBox.setSelectedIndex(macroNum);
				boolean repeats = (parts.length >= 3) && (Integer.parseInt(parts[2]) != 0);
				repeatsCheckBox.setSelected(repeats);
            } else if ("b".equals(type)) {
				bankButton.setSelected(true);
				bankSelectionBox.setSelectedIndex((parts.length >= 2) ? Integer.parseInt(parts[1]) : 0);
            } else {
                buttonGroup.clearSelection();
                passthroughText.setText("Unassigned");
            }
		} catch(NumberFormatException | ArrayIndexOutOfBoundsException e) {
			System.err.println("Failed to parse binding property: " + val);
			passthroughButton.setSelected(true); // Fallback to a safe default.
		}
		
		updateComponentStateAndSave(); // Sync UI component states.
		loadingData = false;
	}
	
	/**
	 * Opens a JColorChooser dialog to change the G13's screen color for the current profile.
	 */
	private void changeScreenColor() {
		final Color currentColor = colorChangeButton.getBackground();
		final Color newColor = JColorChooser.showDialog(this, "Choose Screen Color", currentColor);
		
		if (newColor == null) return; // User cancelled the dialog.
		
		// Store color as an "R,G,B" string.
		bindings.setProperty("color", newColor.getRed() + "," + newColor.getGreen() + "," + newColor.getBlue());
		setColorButton(newColor);
		
		try {
			Configs.saveBindings(bindingsId, bindings);
            firePropertyChange("bindingsSaved", false, true);
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Could not save color setting: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	private void setColorButton(Color color) {
		colorChangeButton.setBackground(color);
		double luminance = (0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue()) / 255.0;
		colorChangeButton.setForeground(luminance > 0.55 ? Color.BLACK : Color.WHITE);
	}
	
	/**
	 * Factory method to create the color selection panel.
	 * @return The configured JPanel for color selection.
	 */
	private JPanel createColorPanel() {
		final JPanel p = new JPanel(new GridBagLayout());
		p.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0; c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(0, 0, 8, 12);
		p.add(new JLabel("Screen color"), c);
		c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 8, 0);
		p.add(colorChangeButton, c);
		c.gridx = 0; c.gridy = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.insets = new Insets(0, 0, 0, 12);
		p.add(new JLabel("Joystick mode"), c);
		c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 0, 0);
		p.add(joystickMode, c);
		colorChangeButton.addActionListener(e -> changeScreenColor());
		return p;
	}

	private void saveJoystickMode() {
		if (loadingData || bindings == null || bindingsId < 0) return;
		bindings.setProperty("stick", joystickMode.getSelectedIndex() == 1 ? "absolute" : "keys");
		try {
            Configs.saveBindings(bindingsId, bindings);
            firePropertyChange("bindingsSaved", false, true);
        }
		catch (IOException error) {
			JOptionPane.showMessageDialog(this, "Could not save joystick mode: " + error.getMessage(),
					"Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	/**
	 * Saves the current state of the UI controls as a binding for the selected key.
	 * This method is called whenever a relevant control is changed.
	 */
	private void saveBindings() {
		if (key == null || loadingData) {
			return; // Do nothing if no key is selected or if data is being loaded.
		}
		
		String prop = "G" + key.getG13KeyCode();
		if (passthroughButton.isSelected()) {
			// Format the passthrough binding string and save it.
			String val = "p,k." + passthroughCode;
			bindings.put(prop, val);
			
			// Update the key's display properties for the ImageMap.
			key.setMappedValue(passthroughText.getText().trim());
			key.setRepeats("N/A");
		} else if (macroButton.isSelected()) {
			// Format the macro binding string and save it.
			int macroNum = macroSelectionBox.getSelectedIndex();
			int repeats = repeatsCheckBox.isSelected() ? 1 : 0;
			String val = "m," + macroNum + "," + repeats;
			bindings.put(prop, val);
			
			// Update the key's display properties.
			if (macros != null && macroNum >= 0 && macroNum < macros.length) {
				final String macroName = macros[macroNum].getProperty("name", "Unnamed Macro");
				key.setMappedValue("Macro: " + macroName);
				key.setRepeats(repeats == 1 ? "Yes" : "No");
			}
		} else if (bankButton.isSelected()) {
			int bank = bankSelectionBox.getSelectedIndex();
			bindings.put(prop, "b," + bank);
			key.setMappedValue("Switch layout: M" + (bank + 1));
			key.setRepeats("N/A");
		} else {
			// If neither button is selected, the key is unassigned.
			bindings.remove(prop);
			key.setMappedValue("Unassigned");
			key.setRepeats("N/A");
		}
		
		// Persist the changes to the properties file.
		try {
			Configs.saveBindings(bindingsId, bindings);
            firePropertyChange("bindingsSaved", false, true);
		} catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Can't Save Bindings: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
}
