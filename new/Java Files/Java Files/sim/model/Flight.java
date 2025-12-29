package sim.model;

// Flight.java

import java.time.LocalTime;

public class Flight {
    private String flightNumber;
    private LocalTime departureTime;
    private int seats;
    private double fillPercent;
    private ShapeType shape;

    public enum ShapeType { CIRCLE, TRIANGLE, SQUARE, PENTAGON, HEXAGON, OCTAGON }

    public Flight(String flightNumber, LocalTime departureTime, int seats, double fillPercent, ShapeType shape) {
        this.flightNumber = flightNumber;
        this.departureTime = departureTime;
        this.seats = seats;
        this.fillPercent = fillPercent;
        this.shape = shape;
    }

    public String getFlightNumber() { return flightNumber; }
    public void setFlightNumber(String flightNumber) { this.flightNumber = flightNumber; }
    public LocalTime getDepartureTime() { return departureTime; }
    public void setDepartureTime(LocalTime departureTime) { this.departureTime = departureTime; }
    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }
    public double getFillPercent() { return fillPercent; }
    public void setFillPercent(double fillPercent) { this.fillPercent = fillPercent; }
    public ShapeType getShape() { return shape; }
    public void setShape(ShapeType shape) { this.shape = shape; }
}
