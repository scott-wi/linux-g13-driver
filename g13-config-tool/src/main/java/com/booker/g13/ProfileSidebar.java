package com.booker.g13;

import java.awt.*;
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
        void importRequested();
        void error(Exception error);
    }

    private final ProfileStore store;
    private final Listener listener;
    private final DefaultListModel<ProfileStore.Profile> model = new DefaultListModel<>();
    private final JList<ProfileStore.Profile> list = new JList<>(model);
    private final JTextField application = new JTextField();
    private final JCheckBox persistent = new JCheckBox("Persistent profile");
    private final JLabel selectionStatus = new JLabel();
    private final Map<Path, ImageIcon> icons = new HashMap<>();
    private boolean refreshing;

    public ProfileSidebar(ProfileStore store, Listener listener) {
        this.store = store;
        this.listener = listener;
        setLayout(new BorderLayout(6, 6));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(290, 700));

        JLabel title = new JLabel("Profiles");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 2));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(64);
        list.setCellRenderer(new ProfileRenderer());
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !refreshing && list.getSelectedValue() != null) {
                showDetails(list.getSelectedValue());
                listener.selected(list.getSelectedValue());
            }
        });
        add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBorder(BorderFactory.createTitledBorder("Profile selection"));
        details.add(new JLabel("Application executable"));
        application.setToolTipText("Executable name, for example game or game.exe");
        details.add(application);

        JPanel editButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        JButton save = new JButton("Save app");
        JButton chooseIcon = new JButton("Choose icon…");
        editButtons.add(save);
        editButtons.add(chooseIcon);
        details.add(editButtons);

        JButton makeDefault = new JButton("Set as default");
        details.add(makeDefault);
        details.add(persistent);
        selectionStatus.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        details.add(selectionStatus);
        JButton importButton = new JButton("Import Windows profile…");
        details.add(importButton);
        add(details, BorderLayout.SOUTH);

        save.addActionListener(event -> updateSelected(null));
        chooseIcon.addActionListener(event -> chooseIcon());
        makeDefault.addActionListener(event -> setDefault());
        persistent.addActionListener(event -> setPersistent());
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
        application.setText(profile.applications().isEmpty() ? "" : profile.applications().get(0));
        try {
            ProfileStore.Profile defaultProfile = store.defaultProfile();
            var persistentProfile = store.persistentProfile();
            persistent.setSelected(persistentProfile.map(value -> value.id().equals(profile.id())).orElse(false));
            String status = profile.id().equals(defaultProfile.id()) ? "Default fallback" : "";
            if (persistent.isSelected()) status = "Persistent override";
            selectionStatus.setText(status.isEmpty() ? "Matched when its app is running" : status);
        } catch (IOException error) { listener.error(error); }
    }

    private void updateSelected(Path icon) {
        try {
            ProfileStore.Profile updated = store.update(list.getSelectedValue(), application.getText(), icon);
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

    private void setDefault() {
        try {
            store.setDefault(list.getSelectedValue());
            refresh(list.getSelectedValue());
        } catch (IOException error) { listener.error(error); }
    }

    private void setPersistent() {
        if (refreshing) return;
        try {
            if (persistent.isSelected()) store.setPersistent(list.getSelectedValue());
            else store.clearPersistent();
            refresh(list.getSelectedValue());
        } catch (IOException error) { listener.error(error); }
    }

    private final class ProfileRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> owner, Object value,
                int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(owner, value, index, selected, focus);
            ProfileStore.Profile profile = (ProfileStore.Profile) value;
            String state;
            try {
                state = profile.id().equals(store.defaultProfile().id()) ? "Default" :
                        profile.applications().isEmpty() ? "No application" : profile.applications().get(0);
                if (store.persistentProfile().map(candidate -> candidate.id().equals(profile.id())).orElse(false)) state = "Persistent";
            } catch (IOException error) { state = "Configuration error"; }
            setText("<html><b>" + html(profile.name()) + "</b><br><span style='color:gray'>" + html(state) + "</span></html>");
            setIcon(loadIcon(profile));
            setIconTextGap(10);
            setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
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
            graphics.setColor(new Color(48, 76, 116));
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
