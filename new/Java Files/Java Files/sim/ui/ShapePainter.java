package sim.ui;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import sim.model.Flight.ShapeType;


/**
 * Utility class responsible for rendering passenger shapes with borders.
 */
public class ShapePainter {
    private ShapePainter() {
        // Prevent instantiation
    }


    /**
     * Draws a filled shape with a thicker colored border.
     *
     * @param g            the Graphics context
     * @param type         the shape type
     * @param x            top-left x
     * @param y            top-left y
     * @param w            width
     * @param h            height
     * @param borderColor  color of the border
     */
    public static void paintShape(Graphics g,
                                  ShapeType type,
                                  int x, int y,
                                  int w, int h,
                                  Color borderColor) {
        Graphics2D g2 = (Graphics2D) g;
        Color originalColor = g2.getColor();
        java.awt.Stroke originalStroke = g2.getStroke();


        // Fill shape
        switch (type) {
            case CIRCLE:
                g2.fillOval(x, y, w, h);
                break;
            case TRIANGLE:
                int[] xsT = { x + w / 2, x, x + w };
                int[] ysT = { y, y + h, y + h };
                g2.fillPolygon(xsT, ysT, 3);
                break;
            case SQUARE:
                g2.fillRect(x, y, w, h);
                break;
            default:
                g2.fillOval(x, y, w, h);
        }


        // Draw thicker border
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(2.5f));  // <-- thicker border


        switch (type) {
            case CIRCLE:
                g2.drawOval(x, y, w, h);
                break;
            case TRIANGLE:
                int[] xsB = { x + w / 2, x, x + w };
                int[] ysB = { y, y + h, y + h };
                g2.drawPolygon(xsB, ysB, 3);
                break;
            case SQUARE:
                g2.drawRect(x, y, w, h);
                break;
            default:
                g2.drawOval(x, y, w, h);
        }


        // Reset graphics state
        g2.setStroke(originalStroke);
        g2.setColor(originalColor);
    }
}
