package com.booker.g13;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Vertical profile browser and editor for icon, application, and selection rules. */
public final class ProfileSidebar extends JPanel {
    public interface Listener {
        void selected(ProfileStore.Profile profile);
        boolean profileChangeAllowed();
        void importRequested();
        void themeChanged(boolean dark);
        void error(Exception error);
    }

    private final ProfileStore store;
    private final Listener listener;
    private final DefaultListModel<ProfileStore.Profile> model = new DefaultListModel<>();
    private final JList<ProfileStore.Profile> list = new JList<>(model) {
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
    };
    private final JTextField profileName = new JTextField();
    private final JTextField application = new JTextField();
    private final JCheckBox darkMode = new JCheckBox("Dark mode", UiTheme.isDark());
    private final Map<Path, ImageIcon> icons = new HashMap<>();
    private boolean refreshing;

    public ProfileSidebar(ProfileStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 6));
        setPreferredSize(new Dimension(350, 720));

        JLabel title = new JLabel("Profiles");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.add(title, BorderLayout.WEST);
        heading.add(darkMode, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(72);
        list.setCellRenderer(new ProfileRenderer());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !refreshing && list.getSelectedValue() != null) {
                showDetails(list.getSelectedValue());
                listener.selected(list.getSelectedValue());
            }
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { maybeShowProfileMenu(event); }
            @Override public void mouseReleased(MouseEvent event) { maybeShowProfileMenu(event); }
        });
        JPanel browser = new JPanel(new BorderLayout(0, 8));
        JScrollPane profileScroll = new JScrollPane(list);
        profileScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        browser.add(profileScroll, BorderLayout.CENTER);
        JPanel profileButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        JButton newButton = new JButton("New…");
        JButton importButton = new JButton("Import…");
        profileButtons.add(newButton);
        profileButtons.add(importButton);
        browser.add(profileButtons, BorderLayout.SOUTH);
        add(browser, BorderLayout.CENTER);

        JPanel details = new JPanel(new GridBagLayout());
        details.setBorder(UiTheme.sectionBorder("Profile details"));
        GridBagConstraints row = new GridBagConstraints();
        row.gridx = 0;
        row.gridy = 0;
        row.weightx = 1;
        row.fill = GridBagConstraints.HORIZONTAL;
        row.anchor = GridBagConstraints.WEST;
        row.insets = new Insets(0, 0, 5, 0);
        details.add(new JLabel("Profile name"), row);
        row.gridy++;
        row.insets = new Insets(0, 0, 10, 0);
        details.add(profileName, row);
        row.gridy++;
        row.insets = new Insets(0, 0, 5, 0);
        details.add(new JLabel("Application executable"), row);
        application.setToolTipText("Executable name, for example game or game.exe");
        row.gridy++;
        row.insets = new Insets(0, 0, 10, 0);
        details.add(application, row);

        JPanel editButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        JButton save = new JButton("Save details");
        JButton chooseIcon = new JButton("Choose icon…");
        editButtons.add(save);
        editButtons.add(chooseIcon);
        row.gridy++;
        details.add(editButtons, row);

        add(details, BorderLayout.SOUTH);

        save.addActionListener(event -> updateSelected(null));
        chooseIcon.addActionListener(event -> chooseIcon());
        darkMode.addActionListener(event -> listener.themeChanged(darkMode.isSelected()));
        newButton.addActionListener(event -> createProfile());
        importButton.addActionListener(event -> listener.importRequested());
    }

    public void refresh(ProfileStore.Profile selection) {
        refreshing = true;
        try {
            String selectedId = selection == null ? null : selection.id();
            model.clear();
            ProfileStore.Profile selected = null;
            for (ProfileStore.Profile profile : store.list()) {
                model.addElement(profile);
                if (profile.id().equals(selectedId)) selected = profile;
            }
            if (selected == null && !model.isEmpty()) selected = model.firstElement();
            list.setSelectedValue(selected, true);
            showDetails(selected);
        } catch (IOException error) {
            listener.error(error);
        } finally {
            refreshing = false;
        }
    }

    public ProfileStore.Profile selected() { return list.getSelectedValue(); }

    private void showDetails(ProfileStore.Profile profile) {
        if (profile == null) return;
        profileName.setText(profile.name());
        application.setText(profile.applications().isEmpty() ? "" : profile.applications().get(0));
        try {
            // Resolve selection metadata here so configuration errors are still surfaced promptly.
            store.defaultProfile();
            store.persistentProfile();
        } catch (IOException error) { listener.error(error); }
    }

    private void updateSelected(Path icon) {
        try {
            ProfileStore.Profile updated = store.update(list.getSelectedValue(), profileName.getText(), application.getText(), icon);
            icons.clear();
            refresh(updated);
            listener.selected(updated);
        } catch (IOException | IllegalArgumentException error) {
            listener.error(error);
        }
    }

    private void chooseIcon() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose profile icon");
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg", "gif", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
            updateSelected(chooser.getSelectedFile().toPath());
    }

    private void setDefault(ProfileStore.Profile profile) {
        try {
            store.setDefault(profile);
            refresh(profile);
        } catch (IOException error) { listener.error(error); }
    }

    private void setPersistent(ProfileStore.Profile profile, boolean enabled) {
        try {
            if (enabled) store.setPersistent(profile);
            else store.clearPersistent();
            refresh(profile);
            if (enabled) listener.selected(profile);
        } catch (IOException error) { listener.error(error); }
    }

    private void createProfile() {
        if (!listener.profileChangeAllowed()) return;
        String name = JOptionPane.showInputDialog(this, "Profile name", "New profile", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        try {
            ProfileStore.Profile created = store.create(name);
            refresh(created);
            listener.selected(created);
        } catch (IOException | IllegalArgumentException error) { listener.error(error); }
    }

    private void deleteProfile(ProfileStore.Profile profile) {
        if (!listener.profileChangeAllowed()) return;
        if (JOptionPane.showConfirmDialog(this, "Delete profile ‘" + profile.name() + "’?",
                "Delete profile", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            store.delete(profile);
            icons.clear();
            refresh(store.defaultProfile());
            listener.selected(list.getSelectedValue());
        } catch (IOException | IllegalArgumentException error) { listener.error(error); }
    }

    private void maybeShowProfileMenu(MouseEvent event) {
        int index = list.locationToIndex(event.getPoint());
        if (index < 0) return;
        Rectangle bounds = list.getCellBounds(index, index);
        if (bounds == null || !bounds.contains(event.getPoint())) return;
        boolean menuButton = SwingUtilities.isLeftMouseButton(event) && event.getID() == MouseEvent.MOUSE_PRESSED
                && event.getX() >= bounds.x + bounds.width - 42;
        if (!menuButton && !event.isPopupTrigger()) return;
        ProfileStore.Profile profile = model.get(index);
        list.setSelectedIndex(index);
        JPopupMenu menu = profileMenu(profile);
        menu.show(list, bounds.x + bounds.width - menu.getPreferredSize().width, bounds.y + bounds.height - 4);
        event.consume();
    }

    JPopupMenu profileMenu(ProfileStore.Profile profile) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem makeDefault = new JMenuItem("Set Default");
        JCheckBoxMenuItem makePersistent = new JCheckBoxMenuItem("Set Persistent");
        JMenuItem delete = new JMenuItem("Delete");
        try {
            makeDefault.setEnabled(!store.defaultProfile().id().equals(profile.id()));
            makePersistent.setSelected(store.persistentProfile()
                    .map(candidate -> candidate.id().equals(profile.id())).orElse(false));
        } catch (IOException error) { listener.error(error); }
        makeDefault.addActionListener(action -> setDefault(profile));
        makePersistent.addActionListener(action -> setPersistent(profile, makePersistent.isSelected()));
        delete.addActionListener(action -> deleteProfile(profile));
        delete.setEnabled(!profile.id().equals("default"));
        menu.add(makeDefault);
        menu.add(makePersistent);
        menu.addSeparator();
        menu.add(delete);
        return menu;
    }

    private final class ProfileRenderer extends JPanel implements ListCellRenderer<ProfileStore.Profile> {
        private final JLabel content = new JLabel();
        private final JLabel menu = new JLabel("⋮", SwingConstants.CENTER);

        ProfileRenderer() {
            super(new BorderLayout(8, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));
            menu.setFont(menu.getFont().deriveFont(Font.BOLD, 22f));
            menu.setPreferredSize(new Dimension(32, 48));
            menu.setToolTipText("Profile actions");
            add(content, BorderLayout.CENTER);
            add(menu, BorderLayout.EAST);
        }

        @Override public Component getListCellRendererComponent(JList<? extends ProfileStore.Profile> owner,
                ProfileStore.Profile profile, int index, boolean selected, boolean focus) {
            String state;
            try {
                state = profile.id().equals(store.defaultProfile().id()) ? "Default" :
                        profile.applications().isEmpty() ? "No application" : profile.applications().get(0);
                if (store.persistentProfile().map(candidate -> candidate.id().equals(profile.id())).orElse(false)) state = "Persistent";
            } catch (IOException error) { state = "Configuration error"; }
            String detail = selected ? html(state) : "<span style='color:" + UiTheme.mutedHex() + "'>" + html(state) + "</span>";
            content.setText("<html><b>" + html(profile.name()) + "</b><br>" + detail + "</html>");
            content.setIcon(loadIcon(profile));
            content.setIconTextGap(10);
            Color background = selected ? owner.getSelectionBackground() : owner.getBackground();
            Color foreground = selected ? owner.getSelectionForeground() : owner.getForeground();
            setBackground(background);
            content.setForeground(foreground);
            menu.setForeground(foreground);
            setOpaque(true);
            setSize(Math.max(1, owner.getWidth()), owner.getFixedCellHeight());
            doLayout();
            return this;
        }

        private String html(String value) {
            return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private ImageIcon loadIcon(ProfileStore.Profile profile) {
            if (profile.icon() != null) return icons.computeIfAbsent(profile.icon(), path -> {
                try {
                    BufferedImage image = ImageIO.read(path.toFile());
                    return new ImageIcon(image.getScaledInstance(48, 48, Image.SCALE_SMOOTH));
                } catch (IOException error) { return fallback(profile); }
            });
            return fallback(profile);
        }

        private ImageIcon fallback(ProfileStore.Profile profile) {
            BufferedImage image = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(UiTheme.accent());
            graphics.fillRoundRect(0, 0, 48, 48, 12, 12);
            graphics.setColor(Color.WHITE);
            graphics.setFont(UIManager.getFont("Label.font").deriveFont(Font.BOLD, 20f));
            String letter = profile.name().isBlank() ? "?" : profile.name().substring(0, 1).toUpperCase();
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.drawString(letter, (48 - metrics.stringWidth(letter)) / 2, 31);
            graphics.dispose();
            return new ImageIcon(image);
        }
    }
}
