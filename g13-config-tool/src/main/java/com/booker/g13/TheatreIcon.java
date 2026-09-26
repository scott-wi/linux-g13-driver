package com.booker.g13;

import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;

/** Renders the bundled 24-unit SVG polylines as theme-aware Swing vector strokes. */
final class TheatreIcon implements Icon {
    private static final List<Path2D> CORNERS = loadCorners();
    private static final int SIZE = 22;

    private static List<Path2D> loadCorners() {
        try (var input = TheatreIcon.class.getResourceAsStream("/com/booker/g13/images/theatre.svg")) {
            if (input == null) throw new IllegalStateException("Missing theatre.svg");
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var lines = factory.newDocumentBuilder().parse(input).getElementsByTagName("polyline");
            List<Path2D> paths = new ArrayList<>();
            for (int i = 0; i < lines.getLength(); i++) {
                String[] points = ((Element) lines.item(i)).getAttribute("points").trim().split("[ ,]+");
                Path2D path = new Path2D.Double();
                for (int j = 0; j < points.length; j += 2) {
                    double x = Double.parseDouble(points[j]), y = Double.parseDouble(points[j + 1]);
                    if (j == 0) path.moveTo(x, y);
                    else path.lineTo(x, y);
                }
                paths.add(path);
            }
            return List.copyOf(paths);
        } catch (Exception error) {
            throw new IllegalStateException("Cannot load theatre.svg", error);
        }
    }

    @Override public int getIconWidth() { return SIZE; }
    @Override public int getIconHeight() { return SIZE; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(x, y);
            g.scale(SIZE / 24.0, SIZE / 24.0);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(component.getForeground());
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (Path2D corner : CORNERS) g.draw(corner);
        } finally { g.dispose(); }
    }
}
