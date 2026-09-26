package com.booker.g13;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

/** Scalable G13 image map. The image, outlines, and hit testing share one transform. */
public class ImageMap extends JComponent {
    private static final long serialVersionUID = 1L;
    public static final ImageIcon G13_KEYPAD = ImageIconHelper.loadEmbeddedImage("/com/booker/g13/images/g13.gif");
    private final List<ImageMapListener> listeners = new ArrayList<>();
    private Key selected;
    private Key mouseover;
    private boolean focusKeys;

    public ImageMap() {
        setPreferredSize(new Dimension(G13_KEYPAD.getIconWidth(), G13_KEYPAD.getIconHeight()));
        setMinimumSize(new Dimension(320, 460));
        setToolTipText(" ");
        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                Key next = keyAt(event.getPoint());
                if (next == mouseover) return;
                mouseover = next;
                repaint();
                fireMouseover();
            }

            @Override public void mouseExited(MouseEvent event) {
                if (mouseover == null) return;
                mouseover = null;
                repaint();
                fireMouseover();
            }

            @Override public void mouseClicked(MouseEvent event) {
                Key next = keyAt(event.getPoint());
                if (next == selected) return;
                selected = next;
                repaint();
                fireSelected();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    public void addListener(ImageMapListener listener) {
        synchronized (listeners) { listeners.add(listener); }
    }

    public void removeListener(ImageMapListener listener) {
        synchronized (listeners) { listeners.remove(listener); }
    }

    private void fireSelected() {
        synchronized (listeners) { for (ImageMapListener listener : listeners) listener.selected(selected); }
    }

    private void fireMouseover() {
        synchronized (listeners) { for (ImageMapListener listener : listeners) listener.mouseover(mouseover); }
    }

    public void setFocusKeys(boolean focusKeys) {
        if (this.focusKeys == focusKeys) return;
        this.focusKeys = focusKeys;
        mouseover = null;
        repaint();
    }

    private Rectangle viewBounds() {
        if (!focusKeys) return new Rectangle(0, 0, G13_KEYPAD.getIconWidth(), G13_KEYPAD.getIconHeight());
        Rectangle keys = null;
        for (Key key : Key.getAllMasks()) {
            if (keys == null) keys = key.getShape().getBounds();
            else keys.add(key.getShape().getBounds());
        }
        keys.grow(12, 12);
        // Focus vertically on the controls, but keep the complete chassis width.
        // Key polygons are inset from the image silhouette, especially on the left.
        keys.x = 0;
        keys.width = G13_KEYPAD.getIconWidth();
        return keys;
    }

    private AffineTransform imageTransform() {
        Rectangle view = viewBounds();
        double scale = Math.min((double) getWidth() / view.width, (double) getHeight() / view.height);
        if (!Double.isFinite(scale) || scale <= 0) scale = 1;
        double x = (getWidth() - view.width * scale) / 2.0 - view.x * scale;
        double y = (getHeight() - view.height * scale) / 2.0 - view.y * scale;
        AffineTransform transform = AffineTransform.getTranslateInstance(x, y);
        transform.scale(scale, scale);
        return transform;
    }

    private Key keyAt(Point point) {
        try {
            Point2D imagePoint = imageTransform().inverseTransform(point, null);
            return Key.getKeyAt((int) Math.floor(imagePoint.getX()), (int) Math.floor(imagePoint.getY()));
        } catch (NoninvertibleTransformException error) {
            return null;
        }
    }

    Point imagePointToComponent(double x, double y) {
        Point2D result = imageTransform().transform(new Point2D.Double(x, y), null);
        return new Point((int) Math.round(result.getX()), (int) Math.round(result.getY()));
    }

    Key keyAtComponent(Point point) { return keyAt(point); }
    Key selectedKey() { return selected; }

    @Override public String getToolTipText(MouseEvent event) {
        Key key = keyAt(event.getPoint());
        if (key == null) return null;
        return "<html><b>" + keyName(key) + "</b><br>" + html(key.getMappedValue())
                + ("N/A".equals(key.getRepeats()) ? "" : "<br>Repeats: " + html(key.getRepeats())) + "</html>";
    }

    private static String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform imageTransform = imageTransform();
            double scale = imageTransform.getScaleX();
            g.transform(imageTransform);
            g.clip(viewBounds());
            g.drawImage(G13_KEYPAD.getImage(), 0, 0, this);
            g.setStroke(new BasicStroke((float) Math.max(0.8, 1.25 / Math.max(scale, 0.01))));
            if (selected != null) {
                g.setColor(UiTheme.selectedFill());
                g.fill(selected.getShape());
                g.setColor(UiTheme.accent());
                g.draw(selected.getShape());
            }
            if (mouseover != null && mouseover != selected) {
                g.setColor(UiTheme.hoverFill());
                g.fill(mouseover.getShape());
                g.setColor(UiTheme.outline());
                g.draw(mouseover.getShape());
            }
            paintBindingLabels(g, scale);
            Color outline = UiTheme.outline();
            g.setColor(new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 150));
            for (Key key : Key.getAllMasks()) g.draw(key.getShape());
        } finally {
            g.dispose();
        }
    }
    static String keyName(Key key) {
        int code = key.getG13KeyCode();
        if (code < 22) return "G" + (code + 1);
        return switch (code) {
            case 24 -> "Light";
            case 25, 26, 27, 28 -> "LCD " + (code - 24);
            case 29, 30, 31 -> "M" + (code - 28);
            case 32 -> "MR";
            case 33 -> "Thumb 1";
            case 34 -> "Thumb 2";
            case 35 -> "Stick";
            case 36 -> "Up";
            case 37 -> "Left";
            case 38 -> "Right";
            case 39 -> "Down";
            default -> "G" + code;
        };
    }

    static String bindingLabel(Key key) {
        String value = key.getMappedValue();
        for (String prefix : List.of("Macro: ", "Chord: ", "Switch layout: ")) {
            if (value.startsWith(prefix)) { value = value.substring(prefix.length()); break; }
        }
        if ("Unassigned".equals(value)) return "—";
        return value.replaceAll("\\s+", " ").strip();
    }

    static String elide(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) return text;
        String ellipsis = "…";
        if (metrics.stringWidth(ellipsis) > width) return "";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + ellipsis) > width)
            end = text.offsetByCodePoints(end, -1);
        return text.substring(0, end) + ellipsis;
    }

    // Find a text rectangle wholly inside the polygon, including slanted edge keys.
    static Rectangle labelBounds(Shape shape, int height) {
        Rectangle bounds = shape.getBounds();
        Rectangle best = new Rectangle();
        for (int y = bounds.y + 2; y + height <= bounds.y + bounds.height - 2; y++) {
            int left = bounds.x + 2, right = bounds.x + bounds.width - 2;
            while (left < right && !shape.contains(left, y, 1, height)) left++;
            while (right > left && !shape.contains(right - 1, y, 1, height)) right--;
            if (right - left > best.width || (right - left == best.width
                    && Math.abs(y + height / 2.0 - bounds.getCenterY())
                    < Math.abs(best.getCenterY() - bounds.getCenterY())))
                best = new Rectangle(left, y, right - left, height);
        }
        return best;
    }

    private void paintBindingLabels(Graphics2D graphics, double scale) {
        // The focused view spends extra space on longer names rather than oversized type.
        double size = focusKeys ? Math.max(9 / scale, Math.min(9, 18 / scale)) : Math.max(9, 9 / scale);
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 9).deriveFont((float) size);
        for (Key key : Key.getAllMasks()) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setFont(font);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                FontMetrics metrics = g.getFontMetrics();
                int lineHeight = metrics.getHeight();
                Font headerFont = font.deriveFont(Font.PLAIN, font.getSize2D() * 0.78f);
                FontMetrics headerMetrics = g.getFontMetrics(headerFont);
                int headerHeight = headerMetrics.getHeight();
                boolean twoLines = key.getShape().getBounds().height >= lineHeight + headerHeight + 4;
                Rectangle area = labelBounds(key.getShape(), lineHeight + (twoLines ? headerHeight : 0));
                String label = elide(bindingLabel(key), metrics, area.width - 4);
                if (label.isEmpty() || "…".equals(label)) continue; // Tiny controls retain their full hover description.
                g.clip(key.getShape());
                g.setColor(new Color(12, 18, 27));
                g.fill(key.getShape());
                // Retain visible selection/hover feedback beneath the high-contrast text.
                if (key == selected || key == mouseover) {
                    g.setColor(key == selected ? UiTheme.selectedFill() : UiTheme.hoverFill());
                    g.fill(key.getShape());
                }
                int baseline = area.y + metrics.getAscent();
                if (twoLines) {
                    g.setColor(new Color(167, 190, 215));
                    g.setFont(headerFont);
                    String name = elide(keyName(key), headerMetrics, area.width - 4);
                    g.drawString(name, area.x + (area.width - headerMetrics.stringWidth(name)) / 2,
                            area.y + headerMetrics.getAscent());
                    g.setFont(font);
                    baseline += headerHeight;
                }
                g.setColor(Color.WHITE);
                g.drawString(label, area.x + (area.width - metrics.stringWidth(label)) / 2, baseline);
            } finally { g.dispose(); }
        }
    }

}
