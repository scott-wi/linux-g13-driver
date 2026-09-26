package com.booker.g13;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.Properties;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The main class for the G13 Configuration application.
 * It builds the main UI, orchestrates the different panels, and handles loading
 * and mapping of key bindings.
 */
public class G13 extends JPanel {

	private static final long serialVersionUID = 1L;
	
	/**
	 * The application version, retrieved from the JAR's manifest file. Defaults to "Development".
	 */
	public static final String VERSION = G13.class.getPackage().getImplementationVersion() != null 
			? G13.class.getPackage().getImplementationVersion() 
			: "Development";
	
	/**
	 * The maximum number of macros that can be configured.
	 */
	private static final int MAX_MACROS = 200;

	// UI Components
	private final ImageMap g13Label = new ImageMap(); // The interactive G13 keypad image.
	private final KeybindPanel keybindPanel = new KeybindPanel(); // Panel for editing key bindings.
	private final MacroEditorPanel macroEditorPanel = new MacroEditorPanel(); // Panel for editing macros.
	private final JComboBox<String> layoutSelector = new JComboBox<>(new String[]{"M1", "M2", "M3"});
	private final JLabel activeLayout = new JLabel("Device: checking…");
	private final Timer stateTimer = new Timer(500, event -> refreshDriverState());
	private boolean changingLayout;
    private final JPanel editorSidebar = new JPanel(new BorderLayout(0, 12));
    private final JToggleButton profilesVisible = new JToggleButton("Profiles", true);
    private final JToggleButton editorVisible = new JToggleButton("Editor", true);
    private final JToggleButton theatre = new JToggleButton("Theatre");
    private static final int DIVIDER_SIZE = 8;
    private JSplitPane profilesSplit;
    private JSplitPane editorSplit;


	
	private final ProfileStore profileStore = new ProfileStore(Configs.getRootDir());
    private ProfileSidebar profileSidebar;
    private ProfileStore.Profile editingProfile = ProfileStore.DEFAULT;

    // Data storage
	private final Properties[] keyBindings = new Properties[4]; // Holds the 4 binding profiles (M1, M2, M3, MR).
	private final Properties[] macros = new Properties[MAX_MACROS]; // Holds all configured macros.
	
	/**
	 * Constructor for the main G13 panel.
	 * Initializes layout, loads configuration, and sets up UI components and listeners.
	 */
	public G13() {
		setLayout(new BorderLayout(12, 12));
		setBorder(BorderFactory.createEmptyBorder(0, 6, 12, 12));
		
		try {
            editingProfile = profileStore.persistentProfile().orElse(profileStore.defaultProfile());
            Configs.selectProfile(profileStore.directory(editingProfile));
        } catch (IOException | IllegalArgumentException e) { showProfileError(e); }
        // Load all configurations and initialize the UI.
		if (!loadConfiguration()) throw new IllegalStateException("Cannot load G13 configuration");
		
		// Set the initial bindings to the first profile (M1).
		keybindPanel.setBindings(0, keyBindings[0]);
		
		g13Label.addListener(new ImageMapListener() {
			@Override
			public void selected(Key key) {
				if (key == null) {
					keybindPanel.setSelectedKey(null);
					return;
				}
				
				keybindPanel.setSelectedKey(key);
			}

			@Override
			public void mouseover(Key key) {
				// Currently unused, but preserved for future functionality.
			}			
		});
		
        keybindPanel.addPropertyChangeListener("bindingsSaved", event -> refreshBindingLabels());
        macroEditorPanel.addPropertyChangeListener("macroSaved", event -> refreshBindingLabels());
        profileSidebar = new ProfileSidebar(profileStore, new ProfileSidebar.Listener() {
            @Override public void selected(ProfileStore.Profile profile) {
                if (!profileChangeAllowed()) profileSidebar.refresh(editingProfile);
                else selectProfile(profile);
            }
            @Override public boolean profileChangeAllowed() {
                if (!macroEditorPanel.isRecording()) return true;
                JOptionPane.showMessageDialog(G13.this, "Stop macro recording before changing profiles.");
                return false;
            }
            @Override public void importRequested() { importProfile(); }
            @Override public void themeChanged(boolean dark) {
                UiTheme.setDark(dark, SwingUtilities.getWindowAncestor(G13.this));
                repaint();
            }
            @Override public void error(Exception error) { showProfileError(error); }
        });
        setUsableSidebarSize(profileSidebar);
        // --- UI Assembly ---
		final JPanel p = new JPanel(new BorderLayout());
		p.setBorder(UiTheme.sectionBorder("G13 Keypad"));
		JPanel layoutBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		layoutBar.add(new JLabel("Editing layout"));
		layoutBar.add(layoutSelector);
		activeLayout.setToolTipText("The M layout currently selected on the physical G13");
		layoutBar.add(activeLayout);
		layoutSelector.addActionListener(event -> {
			if (!changingLayout) mapBindings(layoutSelector.getSelectedIndex());
		});
        JPanel viewBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        configureSidebarButton(profilesVisible, "Profiles");
        configureSidebarButton(editorVisible, "Editor");
        updateSidebarButtons(true, true);
        p.add(sidebarHandle(profilesVisible), BorderLayout.WEST);
        p.add(sidebarHandle(editorVisible), BorderLayout.EAST);
        theatre.setToolTipText("Hide both sidebars and zoom to the assignable keys");
        viewBar.add(theatre);
        profilesVisible.addActionListener(event -> setSidebarVisibility(profilesVisible.isSelected(), editorVisible.isSelected()));
        editorVisible.addActionListener(event -> setSidebarVisibility(profilesVisible.isSelected(), editorVisible.isSelected()));
        theatre.addActionListener(event -> setSidebarVisibility(false, false));
        JPanel toolbar = new JPanel(new BorderLayout(0, 4));
        toolbar.add(layoutBar, BorderLayout.NORTH);
        toolbar.add(viewBar, BorderLayout.SOUTH);
        p.add(toolbar, BorderLayout.NORTH);
		p.add(g13Label, BorderLayout.CENTER);
        p.setMinimumSize(new Dimension(Math.max(330, p.getLayout().minimumLayoutSize(p).width), 300));
		
		editorSidebar.setPreferredSize(new Dimension(390, 720));
		editorSidebar.add(keybindPanel, BorderLayout.NORTH);
		editorSidebar.add(macroEditorPanel, BorderLayout.CENTER);
        setUsableSidebarSize(editorSidebar);
        editorSplit = sidebarSplit(p, editorSidebar, 1.0);
        editorSplit.setName("Editor divider");
        profilesSplit = sidebarSplit(profileSidebar, editorSplit, 0.0);
        profilesSplit.setName("Profiles divider");
        add(profilesSplit, BorderLayout.CENTER);
		
		// Provide the macro data to the panels that need it.
		keybindPanel.setMacros(macros);
		macroEditorPanel.setMacros(macros);
		profileSidebar.refresh(editingProfile);
	}

    private static void setUsableSidebarSize(JPanel sidebar) {
        int width = Math.max(sidebar.getPreferredSize().width,
                sidebar.getLayout().minimumLayoutSize(sidebar).width + 12);
        sidebar.setMinimumSize(new Dimension(width, 300));
        sidebar.setPreferredSize(new Dimension(width, 720));
    }

    private static JSplitPane sidebarSplit(java.awt.Component left, java.awt.Component right, double resizeWeight) {
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, left, right);
        split.setResizeWeight(resizeWeight);
        split.setDividerSize(DIVIDER_SIZE);
        split.setBorder(BorderFactory.createEmptyBorder());
        return split;
    }

    private static void configureSidebarButton(JToggleButton button, String name) {
        button.setName(name);
        button.setText(null);
        button.getAccessibleContext().setAccessibleName(name + " sidebar");
        button.setMargin(new java.awt.Insets(10, 5, 10, 5));
    }

    private static JPanel sidebarHandle(JToggleButton button) {
        JPanel rail = new JPanel(new java.awt.GridBagLayout());
        rail.setOpaque(false);
        rail.add(button);
        return rail;
    }

    private void updateSidebarButtons(boolean showProfiles, boolean showEditor) {
        profilesVisible.setIcon(chevron(!showProfiles));
        editorVisible.setIcon(chevron(showEditor));
        profilesVisible.setToolTipText((showProfiles ? "Hide" : "Show") + " profiles sidebar");
        editorVisible.setToolTipText((showEditor ? "Hide" : "Show") + " editor sidebar");
    }

    private static Icon chevron(boolean right) {
        return new Icon() {
            public int getIconWidth() { return 12; }
            public int getIconHeight() { return 18; }
            public void paintIcon(java.awt.Component component, java.awt.Graphics graphics, int x, int y) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                            java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(component.getForeground());
                    g.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND,
                            java.awt.BasicStroke.JOIN_ROUND));
                    int tip = right ? 9 : 3, tail = right ? 3 : 9;
                    g.drawPolyline(new int[] {x + tail, x + tip, x + tail},
                            new int[] {y + 3, y + 9, y + 15}, 3);
                } finally { g.dispose(); }
            }
        };
    }

    private void setSidebarVisibility(boolean showProfiles, boolean showEditor) {
        boolean focusKeys = !showProfiles && !showEditor;
        boolean profilesChanged = showProfiles != profileSidebar.isVisible();
        boolean editorChanged = showEditor != editorSidebar.isVisible();
        profileSidebar.setVisible(showProfiles);
        editorSidebar.setVisible(showEditor);
        profilesSplit.setDividerSize(showProfiles ? DIVIDER_SIZE : 0);
        editorSplit.setDividerSize(showEditor ? DIVIDER_SIZE : 0);
        if (profilesChanged) profilesSplit.setDividerLocation(showProfiles ? profileSidebar.getMinimumSize().width : 0);
        if (editorChanged) editorSplit.setDividerLocation(showEditor
                ? Math.max(0, editorSplit.getWidth() - editorSidebar.getMinimumSize().width - DIVIDER_SIZE) : editorSplit.getWidth());
        profilesVisible.setSelected(showProfiles);
        editorVisible.setSelected(showEditor);
        theatre.setSelected(focusKeys);
        theatre.setVisible(!focusKeys);
        theatre.getParent().setVisible(!focusKeys);
        updateSidebarButtons(showProfiles, showEditor);
        g13Label.setFocusKeys(focusKeys);
        revalidate();
        repaint();
    }

	@Override public void addNotify() {
		super.addNotify();
		refreshDriverState();
		stateTimer.start();
	}

	@Override public void removeNotify() {
		stateTimer.stop();
		super.removeNotify();
	}

	private void refreshDriverState() {
		var state = DriverState.read();
		if (state.isEmpty()) {
			activeLayout.setText("Device: unavailable");
			return;
		}
		var snapshot = state.get();
		String profile = snapshot.profileId().equals(editingProfile.id()) ? "active" : "another profile";
		activeLayout.setText("Device: M" + (snapshot.layout() + 1) + " · " + profile);
	}

    private void selectProfile(ProfileStore.Profile profile) {
        var previous = editingProfile;
        int selectedLayout = layoutSelector.getSelectedIndex();
        Configs.selectProfile(profileStore.directory(profile));
        if (!loadConfiguration()) {
            Configs.selectProfile(profileStore.directory(previous));
            if (profileSidebar != null) profileSidebar.refresh(previous);
            return;
        }
        editingProfile = profile;
        keybindPanel.setMacros(macros);
        macroEditorPanel.setMacros(macros);
        mapBindings(selectedLayout < 0 ? 0 : selectedLayout);
        repaint();
    }

    private void importProfile() {
        if (macroEditorPanel.isRecording()) {
            JOptionPane.showMessageDialog(this, "Stop macro recording before importing a profile.");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import a Logitech Gaming Software profile");
        chooser.setFileFilter(new FileNameExtensionFilter("Logitech XML profiles", "xml"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            var result = LogitechProfileImporter.read(chooser.getSelectedFile().toPath());
            String summary = result.name() + "\n" + result.importedAssignments()
                    + " assignments across M1–M3; " + result.macros().size() + " macros.\n\n"
                    + String.join("\n", result.warnings())
                    + "\n\nImport as a separate profile? Add a Linux executable afterward for automatic selection.";
            JTextArea preview = new JTextArea(summary, 18, 65);
            preview.setEditable(false);
            preview.setLineWrap(true);
            preview.setWrapStyleWord(true);
            preview.setCaretPosition(0);
            if (JOptionPane.showConfirmDialog(this, new JScrollPane(preview), "Review import",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            ProfileStore.Profile imported = profileStore.save(result);
            selectProfile(imported);
            profileSidebar.refresh(imported);
        } catch (IOException | IllegalArgumentException e) { showProfileError(e); }
    }

    private void showProfileError(Exception e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), "Profile error", JOptionPane.ERROR_MESSAGE);
    }

	/**
	 * Loads all key binding profiles and macros from configuration files.
	 * In case of an error, it displays a dialog to the user.
	 */
	private boolean loadConfiguration() {
		try {
            Properties[] loadedBanks = new Properties[4];
            Properties[] loadedMacros = new Properties[MAX_MACROS];
			// Load the 4 binding profiles.
			for (int i = 0; i < keyBindings.length; i++) {
				loadedBanks[i] = Configs.loadBindings(i);
			}
			
			// Load all possible macros.
			for (int i = 0; i < macros.length; i++) {
				loadedMacros[i] = Configs.loadMacro(i);
			}
			
			System.arraycopy(loadedBanks, 0, keyBindings, 0, keyBindings.length);
            System.arraycopy(loadedMacros, 0, macros, 0, macros.length);
            // Apply the first binding profile (M1) by default.
			mapBindings(0);
            return true;
		}
		catch (IOException e) {
			e.printStackTrace();
			JOptionPane.showMessageDialog(this, "Failed to load configuration:\n" + e.getMessage(), "Configuration Error", JOptionPane.ERROR_MESSAGE);
            return false;
		}
	}
		
	/**
	 * Applies a specific binding profile to the keypad UI.
	 * This method updates the visual representation of each key on the ImageMap
	 * to show what it is currently mapped to.
	 * @param bindingNum The index of the editable button layout to apply (0-2).
	 */
	private void mapBindings(int bindingNum) {
		if (bindingNum < 0 || bindingNum > 2) return;
		changingLayout = true;
		layoutSelector.setSelectedIndex(bindingNum);
		changingLayout = false;
		keybindPanel.setSelectedKey(null); // Deselect any key.
		keybindPanel.setBindings(bindingNum, keyBindings[bindingNum]);
		
        refreshBindingLabels();
    }

    private void refreshBindingLabels() {
        int bindingNum = layoutSelector.getSelectedIndex();
        if (bindingNum < 0 || bindingNum > 2) return;
		// Iterate through all possible G-keys to update their display text.
		for (int i = 0; i < 40; i++) { 
			final Key k = Key.getKeyFor(i);
			if (k == null) continue;

			String property = "G" + i;
			String val = keyBindings[bindingNum].getProperty(property);
			
			// Set default display values.
			k.setMappedValue("Unassigned");
			k.setRepeats("N/A");
			if (i >= 36 && i <= 39 && "absolute".equals(keyBindings[bindingNum].getProperty("stick"))) {
				k.setMappedValue("Analog joystick");
				continue;
			}
			
			if (val != null && !val.isBlank()) {
				// The value string is parsed to determine the binding type and value.
				// Format: "p,k.keycode" for passthrough, "m,macroNum,repeats" for macro.
				String[] parts = val.split("[,.]");
				if (parts.length < 2) continue; // Ignore invalid format.

				final String type = parts[0];
				
				try {
					if ("p".equals(type)) { // Passthrough key
						if (parts.length >= 3) {
							int keycode = Integer.parseInt(parts[2]);
							k.setMappedValue(JavaToLinuxKeymapping.cKeyCodeToString(keycode));
						}
					} else if ("c".equals(type)) {
                        k.setMappedValue("Chord: " + java.util.Arrays.stream(parts).skip(1)
                            .map(Integer::parseInt).map(JavaToLinuxKeymapping::cKeyCodeToString)
                            .collect(java.util.stream.Collectors.joining(" + ")));
					} else if ("m".equals(type)) { // Macro
						if (parts.length >= 3) {
							int macroNum = Integer.parseInt(parts[1]);
							if (macroNum >= 0 && macroNum < macros.length) {
								final String macroName = macros[macroNum].getProperty("name", "Unnamed Macro");
								boolean repeats = Integer.parseInt(parts[2]) != 0;
								k.setMappedValue("Macro: " + macroName);
								k.setRepeats(repeats ? "Yes" : "No");
							}
						}
					} else if ("b".equals(type)) {
						int bank = Integer.parseInt(parts[1]);
						if (bank >= 0 && bank < 3) k.setMappedValue("Switch layout: M" + (bank + 1));
					}
				} catch (NumberFormatException e) {
					// Handle cases where the number in the property is malformed.
					System.err.println("Could not parse binding: " + val);
					k.setMappedValue("Parse Error");
				}
			}
		}
        g13Label.repaint();
	}
	
	/**
	 * The main entry point for the application.
	 * @param args Command line arguments (not used).
	 */
	public static void main(String[] args) {
		// Ensure all UI operations are performed on the Event Dispatch Thread (EDT).
        SwingUtilities.invokeLater(() -> {
            UiTheme.initialize();

            final JFrame frame = new JFrame("G13 Configuration Tool, Version " + VERSION);
            frame.setIconImage(ImageMap.G13_KEYPAD.getImage());
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            final G13 g13 = new G13();	
            frame.getContentPane().add(g13, BorderLayout.CENTER);
            
            frame.pack(); // Size the frame to fit its contents.
            frame.setMinimumSize(new Dimension(Math.max(1100, g13.getMinimumSize().width
                    + frame.getInsets().left + frame.getInsets().right), 720));
            frame.setLocationRelativeTo(null); // Center the frame on the screen.
            frame.setVisible(true);
        });
	}
}
