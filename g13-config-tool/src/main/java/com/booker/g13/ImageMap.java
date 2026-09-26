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

    private AffineTransform imageTransform() {
        int imageWidth = G13_KEYPAD.getIconWidth();
        int imageHeight = G13_KEYPAD.getIconHeight();
        double scale = Math.min((double) getWidth() / imageWidth, (double) getHeight() / imageHeight);
        if (!Double.isFinite(scale) || scale <= 0) scale = 1;
        double x = (getWidth() - imageWidth * scale) / 2.0;
        double y = (getHeight() - imageHeight * scale) / 2.0;
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

    @Override public String getToolTipText(MouseEvent event) {
        Key key = keyAt(event.getPoint());
        if (key == null) return null;
        return "<html><b>G" + key.getG13KeyCode() + "</b><br>" + html(key.getMappedValue())
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
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform imageTransform = imageTransform();
            double scale = imageTransform.getScaleX();
            g.transform(imageTransform);
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
            Color outline = UiTheme.outline();
            g.setColor(new Color(outline.getRed(), outline.getGreen(), outline.getBlue(), 150));
            for (Key key : Key.getAllMasks()) g.draw(key.getShape());
        } finally {
            g.dispose();
        }
    }
}
