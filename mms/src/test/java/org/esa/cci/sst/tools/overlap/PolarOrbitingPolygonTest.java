package org.esa.cci.sst.tools.overlap;

import org.esa.cci.sst.util.GeometryUtil;
import org.junit.Test;
import org.postgis.Geometry;
import org.postgis.LinearRing;
import org.postgis.Point;
import org.postgis.Polygon;

import java.sql.SQLException;
import java.util.List;

import static org.junit.Assert.*;

public class PolarOrbitingPolygonTest {

    static final Point[] ATSR2_POINTS = {
            new Point(-73.2396621704102, -0.54239547252655),
            new Point(-73.1431732177734, -0.520916402339935),
            new Point(-72.2660293579102, -0.325591802597046),
            new Point(-71.3889312744141, -0.13018998503685),
            new Point(-70.5118408203125, 0.065240204334259),
            new Point(-69.6347427368164, 0.260657608509064),
            new Point(-68.7576141357422, 0.456013113260269),
            new Point(-68.7673797607422, 0.500484824180603),
            new Point(-71.8886871337891, 14.8226184844971),
            new Point(-75.1481475830078, 29.1228923797607),
            new Point(-78.9467544555664, 43.3609962463379),
            new Point(-84.215705871582, 57.4777717590332),
            new Point(-94.4490737915039, 71.280632019043),
            new Point(-138.122802734375, 82.9039154052734),
            new Point(131.216903686523, 77.5100784301758),
            new Point(113.434799194336, 64.1561660766602),
            new Point(106.610000610352, 50.143627166748),
            new Point(102.290626525879, 35.9523048400879),
            new Point(98.8400344848633, 21.6753520965576),
            new Point(95.6899719238281, 7.35762691497803),
            new Point(92.5302658081055, -6.96105051040649),
            new Point(89.0800857543945, -21.2364654541016),
            new Point(84.9273910522461, -35.4125633239746),
            new Point(79.2074356079102, -49.3954772949219),
            new Point(69.4523468017578, -62.9383697509766),
            new Point(45.576171875, -74.9276962280273),
            new Point(-19.5284976959229, -78.5634460449219),
            new Point(-61.037899017334, -68.9600830078125),
            new Point(-75.1144943237305, -55.876392364502),
            new Point(-82.2299346923828, -42.0505752563477),
            new Point(-86.9631881713867, -27.949987411499),
            new Point(-90.6774673461914, -13.7138423919678),
            new Point(-93.9354400634766, 0.589721441268921),
            new Point(-94.0319290161133, 0.568248093128204),
            new Point(-94.9090881347656, 0.372969150543213),
            new Point(-95.7862014770508, 0.177602618932724),
            new Point(-96.6632995605469, -0.0178034286946058),
            new Point(-97.540397644043, -0.213207826018333),
            new Point(-98.4175186157227, -0.408562451601028),
            new Point(-98.4077529907227, -0.453035891056061),
            new Point(-95.2878952026367, -14.7677488327026),
            new Point(-92.0339431762695, -29.0449886322021),
            new Point(-88.2490615844727, -43.2463264465332),
            new Point(-83.0195693969727, -57.3160934448242),
            new Point(-72.9588928222656, -71.0709457397461),
            new Point(-31.0463008880615, -82.763313293457),
            new Point(60.779712677002, -77.8122406005859),
            new Point(79.1510467529297, -64.5553588867188),
            new Point(86.0869140625, -50.6049690246582),
            new Point(90.4426651000977, -36.4617729187012),
            new Point(93.9067077636719, -22.2184600830078),
            new Point(97.0593338012695, -7.9179162979126),
            new Point(100.213653564453, 6.40137577056885),
            new Point(103.64933013916, 20.695405960083),
            new Point(107.772300720215, 34.9073486328125),
            new Point(113.428596496582, 48.9426498413086),
            new Point(123.017532348633, 62.5583114624023),
            new Point(146.300109863281, 74.6796646118164),
            new Point(-149.024063110352, 78.6539688110352),
            new Point(-106.459213256836, 69.1518402099609),
            new Point(-92.1518630981445, 56.0339813232422),
            new Point(-84.9766311645508, 42.164436340332),
            new Point(-80.2226486206055, 28.0279064178467),
            new Point(-76.5002593994141, 13.7687816619873),
            new Point(-73.2396621704102, -0.54239547252655)
    };

    static final Point[] TEST_POLYGON = new Point[]{
            new Point(10, 40),

            new Point(12, 39.6),
            new Point(13, 39.3),
            new Point(14, 39),
            new Point(15, 38.7),
            new Point(16, 38.4),

            new Point(18, 38),
            new Point(20, 48),
            new Point(22, 58),
            new Point(24, 68),
            new Point(26, 78),

            new Point(24, 78.4),
            new Point(23, 78.7),
            new Point(22, 79),
            new Point(21, 79.3),
            new Point(20, 79.6),

            new Point(18, 80),
            new Point(16, 70),
            new Point(14, 60),
            new Point(12, 50),
            new Point(10, 40)
    };
    static final Point[] TEST_POINTS_INSIDE = new Point[]{
            new Point(14, 40),
            new Point(14, 50),
            new Point(22, 60)
    };
    static final Point[] TEST_POINTS_OUTSIDE = new Point[]{
            new Point(22, 80),
            new Point(5, 55),
            new Point(25, 55),
            new Point(15, 5),
            new Point(-5, -10),
            new Point(15, -5)
    };

    @Test
    public void testConstructor() {
        final Geometry geometry = new Polygon(new LinearRing[]{new LinearRing(TEST_POLYGON)});
        final PolarOrbitingPolygon polygon = new PolarOrbitingPolygon(1, System.currentTimeMillis(), geometry);
        assertEquals("number of rings", 2, polygon.getRings().size());
        assertEquals("number of points in first ring", 12, polygon.getRings().get(0).size());
        assertEquals("number of points in second ring", 12, polygon.getRings().get(1).size());
        assertEquals("lat of first point of first ring", 58.0, polygon.getRings().get(0).get(0).getLat(), 1.0e-8);
    }

    @Test
    public void testIsPointInPolygon() {
        final Geometry geometry = new Polygon(new LinearRing[]{new LinearRing(TEST_POLYGON)});
        final PolarOrbitingPolygon polygon = new PolarOrbitingPolygon(1, System.currentTimeMillis(), geometry);
        for (Point point : TEST_POINTS_INSIDE) {
            assertTrue("lat=" + point.getY() + " lon=" + point.getX(), polygon.isPointInPolygon(point.getY(), point.getX()));
        }
        for (Point point : TEST_POINTS_OUTSIDE) {
            assertFalse("lat=" + point.getY() + " lon=" + point.getX(), polygon.isPointInPolygon(point.getY(), point.getX()));
        }
    }

    @Test
    public void testIsPointInRing() {
        Geometry geometry = new Polygon(new LinearRing[]{new LinearRing(TEST_POLYGON)});
        final PolarOrbitingPolygon polygon = new PolarOrbitingPolygon(1, System.currentTimeMillis(), geometry);
        final List<PolarOrbitingPolygon.Point> points = polygon.getRings().get(0);
        assertFalse("lat=" + TEST_POINTS_INSIDE[0].getY() + " lon=" + TEST_POINTS_INSIDE[0].getX(), polygon.isPointInRing(TEST_POINTS_INSIDE[0].getY(), TEST_POINTS_INSIDE[0].getX(), points));
        assertFalse("lat=" + TEST_POINTS_INSIDE[1].getY() + " lon=" + TEST_POINTS_INSIDE[1].getX(), polygon.isPointInRing(TEST_POINTS_INSIDE[1].getY(), TEST_POINTS_INSIDE[1].getX(), points));
        assertTrue("lat=" + TEST_POINTS_INSIDE[2].getY() + " lon=" + TEST_POINTS_INSIDE[2].getX(), polygon.isPointInRing(TEST_POINTS_INSIDE[2].getY(), TEST_POINTS_INSIDE[2].getX(), points));
    }

    @Test
    public void testIsBetween() {
        assertTrue(PolarOrbitingPolygon.isBetween(13.0, 10.0, 20.0));
        assertTrue(PolarOrbitingPolygon.isBetween(13.0, 20.0, 10.0));

        assertFalse(PolarOrbitingPolygon.isBetween(13.0, 10.0, -14.0));
    }

    @Test
    public void testIsEdgeCrossingEquator() {
        assertTrue(PolarOrbitingPolygon.isEdgeCrossingEquator(-1.0, 1.0));
        assertTrue(PolarOrbitingPolygon.isEdgeCrossingEquator(1.0, -1.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingEquator(1.0, 1.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingEquator(-1.0, -1.0));

        assertTrue(PolarOrbitingPolygon.isEdgeCrossingEquator(-10.0, 10.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingEquator(-60.0, -30.0));
    }

    @Test
    public void testAtsr2Polygon() {
        double skip = 2.5;               // 2.5
        final int expectedMatches = 519; // 3259
        for (int rotations = 0; rotations < 360.0 / skip; ++rotations) {
//            System.out.println("cycle " + rotations + " lat=" + ATSR2_POINTS[0].getY() + " lon=" + ATSR2_POINTS[0].getX());
            final Geometry geometry = new Polygon(new LinearRing[]{new LinearRing(ATSR2_POINTS)});
            final PolarOrbitingPolygon polygon = new PolarOrbitingPolygon(1, System.currentTimeMillis(), geometry);
            int orbitMatches = 0;
            for (double lat = -90.0 + skip; lat <= 90.0; lat += skip) {
                //int lineMatches = 0;
                for (double lon = -180.0 + skip; lon <= 180.0; lon += skip) {
                    if (polygon.isPointInPolygon(lat, lon)) {
                        //System.out.print(lon + ", ");
                        //      ++lineMatches;
                        ++orbitMatches;
                    }
                }
                //System.out.println(lineMatches + " points inside at lat=" + lat);
            }
            assertEquals("orbitMatches", expectedMatches, orbitMatches);
            rotate(ATSR2_POINTS, skip);
        }
    }

    private void rotate(Point[] points, double skip) {
        for (Point point : points) {
            point.setX(GeometryUtil.normalizeLongitude(point.getX() + skip));
        }
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingEquator(1.0, 1.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingEquator(-1.0, -1.0));
    }

    @Test
    public void testGetLongitudeAtEquator() {
        double longitudeAtEquator = PolarOrbitingPolygon.getLongitudeAtEquator(12.0, 13.0, 12.0, 14.0);
        assertEquals(13.0, longitudeAtEquator, 1e-8);

        longitudeAtEquator = PolarOrbitingPolygon.getLongitudeAtEquator(12.0, 0.0, 14.0, 15.0);
        assertEquals(-90.0, longitudeAtEquator, 1e-8);  // @todo 1 tb/mb really?

        longitudeAtEquator = PolarOrbitingPolygon.getLongitudeAtEquator(12.0, 13.0, 14.0, 15.0);
        assertEquals(1.0, longitudeAtEquator, 1e-8);

        longitudeAtEquator = PolarOrbitingPolygon.getLongitudeAtEquator(12.0, 13.0, 14.0, 0.0);
        assertEquals(91.0, longitudeAtEquator, 1e-8);   // @todo 1 tb/mb really?

        longitudeAtEquator = PolarOrbitingPolygon.getLongitudeAtEquator(12.0, 15.0, 14.0, 15.0);
        assertEquals(15.0, longitudeAtEquator, 1e-8);

        longitudeAtEquator = PolarOrbitingPolygon.getLongitudeAtEquator(0.0, 1.0, 2.0, 3.0);
        assertEquals(1.0, longitudeAtEquator, 1e-8);

        assertEquals(10.0, PolarOrbitingPolygon.getLongitudeAtEquator(-20.0, 5.0, 40.0, 20.0), 1e-8);
        assertEquals(170.0, PolarOrbitingPolygon.getLongitudeAtEquator(20.0, 175.0, 60.0, -175), 1e-8);
    }

    @Test
    public void testGetLatitudeAtMeridian() {
        double latitudeAtMeridian = PolarOrbitingPolygon.getLatitudeAtMeridian(-19.0, 23.0, -21.0, 23.0);
        assertEquals(-19.0, latitudeAtMeridian, 1e-8);

        latitudeAtMeridian = PolarOrbitingPolygon.getLatitudeAtMeridian(-11.0, 23.0, -11.0, 26.0);
        assertEquals(-11.0, latitudeAtMeridian, 1e-8);

        latitudeAtMeridian = PolarOrbitingPolygon.getLatitudeAtMeridian(8.0, 0.0, -11.0, 26.0);
        assertEquals(8.0, latitudeAtMeridian, 1e-8);

        latitudeAtMeridian = PolarOrbitingPolygon.getLatitudeAtMeridian(-9.0, 10.0, -11.0, 12.0);
        assertEquals(1.0, latitudeAtMeridian, 1e-8);

        latitudeAtMeridian = PolarOrbitingPolygon.getLatitudeAtMeridian(-9.0, 10.0, -11.0, -12.0);
        assertEquals(-9.909090909090908, latitudeAtMeridian, 1e-8);

        latitudeAtMeridian = PolarOrbitingPolygon.getLatitudeAtMeridian(1.0, 2.0, 3.0, 4.0);
        assertEquals(-1.0, latitudeAtMeridian, 1e-8);

        assertEquals(40.0, PolarOrbitingPolygon.getLatitudeAtMeridian(50.0, -40.0, 35.0, 20.0), 1e-8);
        assertEquals(40.0, PolarOrbitingPolygon.getLatitudeAtMeridian(50.0, -40.0, 45.0, -20.0), 1e-8);
    }

    @Test
    public void testIsEdgeCrossingMeridian() {
        assertTrue(PolarOrbitingPolygon.isEdgeCrossingMeridian(-1.0, 1.0));
        assertTrue(PolarOrbitingPolygon.isEdgeCrossingMeridian(0.0, 1.0));
        assertTrue(PolarOrbitingPolygon.isEdgeCrossingMeridian(1.0, -1.0));
        assertTrue(PolarOrbitingPolygon.isEdgeCrossingMeridian(0.0, -1.0));

        assertFalse(PolarOrbitingPolygon.isEdgeCrossingMeridian(-1.0, -1.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingMeridian(1.0, 1.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingMeridian(-0.1, 182.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingMeridian(182.0, -0.1));

        assertTrue(PolarOrbitingPolygon.isEdgeCrossingMeridian(-10.0, 10.0));
        assertTrue(PolarOrbitingPolygon.isEdgeCrossingMeridian(10.0, -10.0));
        assertFalse(PolarOrbitingPolygon.isEdgeCrossingMeridian(-160.0, 160.0));
    }

    @Test
    public void testGetId() throws SQLException {
        final Polygon polygon = new Polygon("POLYGON((10 30, 10.5 30, 11 30, 11.5 30, 12 30, 12 30.5, 12 31, 12 31.5, 12 32, 11.5 32, 11 32, 10.5 32, 10 32, 10 31.5, 10 31, 10 30.5, 10 30))");
        final PolarOrbitingPolygon polarOrbitingPolygon = new PolarOrbitingPolygon(83, 14, polygon);

        assertEquals(83, polarOrbitingPolygon.getId());
    }

    @Test
    public void testGetTime() throws SQLException {
        final Polygon polygon = new Polygon("POLYGON((10 30, 10.5 30, 11 30, 11.5 30, 12 30, 12 30.5, 12 31, 12 31.5, 12 32, 11.5 32, 11 32, 10.5 32, 10 32, 10 31.5, 10 31, 10 30.5, 10 30))");
        final PolarOrbitingPolygon polarOrbitingPolygon = new PolarOrbitingPolygon(30, 1983, polygon);

        assertEquals(1983, polarOrbitingPolygon.getTime());
    }

    @Test
    public void testRectangleInNorthernHemisphere() throws SQLException {
        // remember: WKT is (lon/lat) tb 2014-02-04
        final Polygon polygon = new Polygon("POLYGON((10 30, 10.5 30, 11 30, 11.5 30, 12 30, 12 30.5, 12 31, 12 31.5, 12 32, 11.5 32, 11 32, 10.5 32, 10 32, 10 31.5, 10 31, 10 30.5, 10 30))");
        final PolarOrbitingPolygon polarOrbitingPolygon = new PolarOrbitingPolygon(12, 14, polygon);

        // center
        assertTrue(polarOrbitingPolygon.isPointInPolygon(31, 11));

        // lower left
        assertTrue(polarOrbitingPolygon.isPointInPolygon(30.001, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(29.999, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(30.001, 9.998));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(29.999, 9.998));

        // upper left
        assertTrue(polarOrbitingPolygon.isPointInPolygon(30.001, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(29.999, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(30.001, 12.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(29.999, 12.001));

        // upper right
        assertTrue(polarOrbitingPolygon.isPointInPolygon(31.999, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(32.001, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(31.999, 12.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(32.001, 12.001));

        // lower right
        assertTrue(polarOrbitingPolygon.isPointInPolygon(31.999, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(32.001, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(31.999, 9.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(32.001, 9.999));
    }

    @Test
    public void testRectangleInSouthernHemisphere() throws SQLException {
        // remember: WKT is (lon/lat) tb 2014-02-04
        final Polygon polygon = new Polygon("POLYGON((10 -50, 10.5 -50, 11 -50, 11.5 -50, 12 -50, 12 -50.5, 12 -51, 12 -51.5, 12 -52, 11.5 -52, 11 -52, 10.5 -52, 10 -52, 10 -51.5, 10 -51, 10 -50.5, 10 -50))");
        final PolarOrbitingPolygon polarOrbitingPolygon = new PolarOrbitingPolygon(12, 14, polygon);

        // center
        assertTrue(polarOrbitingPolygon.isPointInPolygon(-51, 11));

        // lower left
        assertTrue(polarOrbitingPolygon.isPointInPolygon(-51.999, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-52.001, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-51.999, 9.998));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-52.001, 9.998));

        // upper left
        assertTrue(polarOrbitingPolygon.isPointInPolygon(-50.001, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-49.999, 10.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-50.001, 9.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-49.999, 9.999));

        // upper right
        assertTrue(polarOrbitingPolygon.isPointInPolygon(-50.001, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-49.999, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-50.001, 12.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-49.999, 12.001));

        // lower right
        assertTrue(polarOrbitingPolygon.isPointInPolygon(-51.999, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-52.001, 11.999));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-51.999, 12.001));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-52.001, 12.001));
    }

    @Test
    public void testRhomboidOnEquator() throws SQLException {
        // remember: WKT is (lon/lat) tb 2014-02-05
        final Polygon polygon = new Polygon("POLYGON((-8 -2, -7.75 -1, -7.5 0, -7.25 1, -7 2, -6 2, -5 2, -4 2, -3 2, -3.25 1, -3.5 0, -3.75 -1, -4 -2, -5 -2, -6 -2, -7 -2, -8 -2))");
        final PolarOrbitingPolygon polarOrbitingPolygon = new PolarOrbitingPolygon(12, 14, polygon);

        // center
        assertTrue(polarOrbitingPolygon.isPointInPolygon(0, -5.5));

        // lower left
        assertTrue(polarOrbitingPolygon.isPointInPolygon(-1.95, -7.95));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-2.05, -7.95));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-1.95, -8.05));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-2.05, -8.05));

        // upper left
        assertTrue(polarOrbitingPolygon.isPointInPolygon(1.95, -6.95));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(2.05, -6.95));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(1.95, -7.05));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(2.05, -7.05));

        // upper right
        assertTrue(polarOrbitingPolygon.isPointInPolygon(1.95, -3.05));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(2.05, -3.05));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(1.95, -2.95));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(2.05, -2.95));

        // lower right
        assertTrue(polarOrbitingPolygon.isPointInPolygon(-1.95, -4.05));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-2.05, -4.05));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-1.95, -3.95));
        assertFalse(polarOrbitingPolygon.isPointInPolygon(-2.05, -3.95));
    }
}