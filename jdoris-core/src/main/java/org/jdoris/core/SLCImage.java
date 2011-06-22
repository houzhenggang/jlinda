package org.jdoris.core;

import org.esa.beam.framework.datamodel.GeoPos;
import org.esa.beam.framework.datamodel.MetadataElement;
import org.esa.beam.framework.datamodel.ProductData;
import org.esa.nest.datamodel.AbstractMetadata;
import org.esa.nest.util.Constants;
import org.esa.nest.util.GeoUtils;
import org.jdoris.core.io.ResFile;

import java.io.File;

public final class SLCImage {

    // TODO: refactor to BuilderPattern

    // file & format
    private File resFileName;
    private String fileName;
    private int formatFlag; // not used

    // sensor
    private String sensor;
    private String sarProcessor;
    private double radar_wavelength; // TODO: close this modifier

    // geo & orientation
    private Point approxRadarCentreOriginal = new Point(); // use PixelPos as double!
    private GeoPos approxGeoCentreOriginal = new GeoPos();
    private Point approxXYZCentreOriginal = new Point();

    private double averageHeight;

    // azimuth annotations
    private double PRF;
    private double azimuthBandwidth;
    private double tAzi1;
    private String azimuthWeightingWindow;

    // range annotations
    private double rsr2x;
    private double rangeBandwidth;
    private double tRange1;
    private String rangeWeightingWindow;

    // ______ offset = X(l,p) - X(L,P) ______
    // ______ Where l,p are in the local slave coordinate system and ______
    // ______ where L,P are in the local master coordinate system ______
    // ______ These variables are stored in the slaveinfo variable only ______
    private int coarseOrbitOffsetL;     // orbit offset in line (azimuth) direction
    private int coarseOrbitOffsetP;     // orbit offset in pixel (range) direction
    private int coarseOffsetL;          // offset in line (azimuth) direction
    private int coarseOffsetP;          // offset in pixel (range) direction

    // oversampling factors
    private int ovsAz;                 // oversampling of SLC
    private int ovsRg;                 // oversampling of SLC

    // multilooking factors
    private int mlAz;                 // multilooking of SLC
    private int mlRg;                 // multilooking of SLC

    // relative to master geometry, or
    // absolute timing error of master
    // relative to master geometry, or
    // absolute timing error of master
    // timing errors
    private int azTimingError;        // timing error in azimuth direction

    // units: lines
    private int rgTimingError;        // timing error in range direction

    // units: pixels
    private boolean absTimingErrorFlag;   // FALSE if master time is NOT updated,

    // true if it is
    //    private static Rectangle originalWindow;       // position and size of the full scene
    Window originalWindow;       // position and size of the full scene
    Window currentWindow;        // position and size of the subset
    Window slaveMasterOffsets;   // overlapping slave window in master coordinates
    public Doppler doppler;

    public SLCImage() {

        this.sensor = "SLC_ERS";                    // default (vs. SLC_ASAR, JERS, RSAT)
        this.sarProcessor = "SARPR_VMP";            // (VMP (esa paf) or ATLANTIS or TUDELFT) // TODO PGS update?
        this.formatFlag = 0;                        // format of file on disk

        this.approxXYZCentreOriginal.x = 0.0;
        this.approxXYZCentreOriginal.y = 0.0;
        this.approxXYZCentreOriginal.z = 0.0;

        this.radar_wavelength = 0.0565646;          // [m] default ERS2
        this.tAzi1 = 0.0;                           // [s] sec of day
        this.tRange1 = 5.5458330 / 2.0e3;           // [s] one way, default ERS2
        this.rangeWeightingWindow = "HAMMING";
        this.rangeBandwidth = 15.55e6;              // [Hz] default ERS2

        this.PRF = 1679.902;                        // [Hz] default ERS2
        this.azimuthBandwidth = 1378.0;             // [Hz] default ERS2
        this.azimuthWeightingWindow = "HAMMING";

        this.rsr2x = 18.9624680 * 2.0e6;            // [Hz] default ERS2

        this.coarseOffsetL = 0;                     // by default
        this.coarseOffsetP = 0;                     // by default
        this.coarseOrbitOffsetL = 0;                // by default
        this.coarseOrbitOffsetP = 0;                // by default

        this.ovsRg = 1;                             // by default
        this.ovsAz = 1;                             // by default

        this.absTimingErrorFlag = false;
        this.azTimingError = 0;                     // by default, unit lines
        this.rgTimingError = 0;                     // by default, unit pixels

        this.currentWindow = new Window(1, 25000, 1, 5000);
        this.originalWindow = new Window(1, 25000, 1, 5000);
//        slavemasteroffsets.l00  = 0;               // window in master coordinates
//        slavemasteroffsets.p00  = 0;
//        slavemasteroffsets.l0N  = 0;
//        slavemasteroffsets.p0N  = 0;
//        slavemasteroffsets.lN0  = 0;
//        slavemasteroffsets.pN0  = 0;
//        slavemasteroffsets.lNN  = 0;
//        slavemasteroffsets.pNN  = 0;

        this.doppler = new Doppler();
        this.doppler.f_DC_a0 = 0.0;
        this.doppler.f_DC_a1 = 0.0;
        this.doppler.f_DC_a2 = 0.0;
//        f_DC_const = (actualDopplerChange() < maximumDopplerChange());

    }

    public SLCImage(MetadataElement element) {

        this();

        // units [meters]
        this.radar_wavelength = (Constants.lightSpeed / Math.pow(10, 6)) / element.getAttributeDouble(AbstractMetadata.radar_frequency);

        // units [Hz]
        this.PRF = element.getAttributeDouble(AbstractMetadata.pulse_repetition_frequency);

        // work with seconds of the day!
        final ProductData.UTC t_azi1_UTC = element.getAttributeUTC(AbstractMetadata.first_line_time);
        this.tAzi1 = (t_azi1_UTC.getMJD() - (int) t_azi1_UTC.getMJD()) * 24 * 3600;

        // 2 times range sampling rate [HZ]
        this.rsr2x = (element.getAttributeDouble(AbstractMetadata.range_sampling_rate) * Math.pow(10, 6) * 2);

        // one way (!!!) time to first range pixels [sec]
        this.tRange1 = element.getAttributeDouble(AbstractMetadata.slant_range_to_first_pixel) / Constants.lightSpeed;

        this.approxRadarCentreOriginal.x = element.getAttributeDouble(AbstractMetadata.num_samples_per_line) / 2.0d;  // x direction is range!
        this.approxRadarCentreOriginal.y = element.getAttributeDouble(AbstractMetadata.num_output_lines) / 2.0d;  // y direction is azimuth

        // TODO: replace computation of the centre using getGeoPos()
        // simple averaging of the corners : as approximation accurate enough
        this.approxGeoCentreOriginal.lat = (float) ((element.getAttributeDouble(AbstractMetadata.first_near_lat) +
                element.getAttributeDouble(AbstractMetadata.first_far_lat) +
                element.getAttributeDouble(AbstractMetadata.last_near_lat) +
                element.getAttributeDouble(AbstractMetadata.last_far_lat)) / 4);

        this.approxGeoCentreOriginal.lon = (float) ((element.getAttributeDouble(AbstractMetadata.first_near_long) +
                element.getAttributeDouble(AbstractMetadata.first_far_long) +
                element.getAttributeDouble(AbstractMetadata.last_near_long) +
                element.getAttributeDouble(AbstractMetadata.last_far_long)) / 4);

        final double[] xyz = new double[3];
        GeoUtils.geo2xyz(getApproxGeoCentreOriginal(), xyz);

        this.approxXYZCentreOriginal.x = xyz[0];
        this.approxXYZCentreOriginal.y = xyz[1];
        this.approxXYZCentreOriginal.z = xyz[2];

        // set dopplers
        final AbstractMetadata.DopplerCentroidCoefficientList[] dopplersArray = AbstractMetadata.getDopplerCentroidCoefficients(element);

        this.doppler.f_DC_a0 = dopplersArray[0].coefficients[0];
        this.doppler.f_DC_a1 = dopplersArray[0].coefficients[1];
        this.doppler.f_DC_a2 = dopplersArray[0].coefficients[2];
        this.doppler.checkConstant();

    }

    public void parseResFile(File resFileName) throws Exception {

        final ResFile resFile = new ResFile(resFileName);

        resFile.setSubBuffer("_Start_readfiles","End_readfiles");

        this.sensor = resFile.parseStringValue("Sensor platform mission identifer");
        this.sarProcessor = resFile.parseStringValue("SAR_PROCESSOR");
        this.radar_wavelength = resFile.parseDoubleValue("Radar_wavelength \\(m\\)");

        this.approxGeoCentreOriginal.lat = (float) resFile.parseDoubleValue("Scene_centre_latitude");
        this.approxGeoCentreOriginal.lon = (float) resFile.parseDoubleValue("Scene_centre_longitude");
        this.averageHeight = 0.0;

        this.approxXYZCentreOriginal = Ellipsoid.ell2xyz(Math.toRadians(approxGeoCentreOriginal.lat),
                Math.toRadians(approxGeoCentreOriginal.lon), averageHeight);

        // azimuth annotations
        this.PRF = resFile.parseDoubleValue("Pulse_Repetition_Frequency \\(computed, Hz\\)");
        this.azimuthBandwidth = resFile.parseDoubleValue("Total_azimuth_band_width \\(Hz\\)");
//        ProductData.UTC tAzi1_UTC = resFile.parseDatTimeValue("First_pixel_azimuth_time \\(UTC\\)");
//        this.tAzi1 = (tAzi1_UTC.getMJD() - tAzi1_UTC.getDaysFraction()) * 24 * 3600;
        this.tAzi1 = resFile.parseTimeValue("First_pixel_azimuth_time \\(UTC\\)");
        this.azimuthWeightingWindow = resFile.parseStringValue("Weighting_azimuth");

        // range annotations
        this.rsr2x = resFile.parseDoubleValue("Range_sampling_rate \\(computed, MHz\\)")*2*Math.pow(10,6);
        this.rangeBandwidth = resFile.parseDoubleValue("Total_range_band_width \\(MHz\\)");
        this.tRange1 = resFile.parseDoubleValue("Range_time_to_first_pixel \\(2way\\) \\(ms\\)")/2/1000;
        this.rangeWeightingWindow = resFile.parseStringValue("Weighting_range");

        // data windows
        final int numberOfLinesTEMP = resFile.parseIntegerValue("Number_of_lines_original");
        final int numberOfPixelsTEMP = resFile.parseIntegerValue("Number_of_pixels_original");
        this.originalWindow = new Window(1, numberOfLinesTEMP, 1, numberOfPixelsTEMP);

        resFile.resetSubBuffer();
        resFile.setSubBuffer("_Start_crop","End_crop");

        // current window
        this.currentWindow.linelo = resFile.parseIntegerValue("First_line \\(w.r.t. original_image\\)");
        this.currentWindow.linehi = resFile.parseIntegerValue("Last_line \\(w.r.t. original_image\\)");
        this.currentWindow.pixlo = resFile.parseIntegerValue("First_pixel \\(w.r.t. original_image\\)");
        this.currentWindow.pixhi = resFile.parseIntegerValue("Last_pixel \\(w.r.t. original_image\\)");

        resFile.resetSubBuffer();
        resFile.setSubBuffer("_Start_readfiles","End_readfiles");
        // doppler
        this.doppler.f_DC_a0 = resFile.parseDoubleValue("Xtrack_f_DC_constant \\(Hz, early edge\\)");
        this.doppler.f_DC_a1 = resFile.parseDoubleValue("Xtrack_f_DC_linear \\(Hz/s, early edge\\)");
        this.doppler.f_DC_a2 = resFile.parseDoubleValue("Xtrack_f_DC_quadratic \\(Hz/s/s, early edge\\)");
        this.doppler.checkConstant();

    }

    /*---  RANGE CONVERSIONS ----*/

    // Convert pixel number to range time (1 is first pixel)
    public double pix2tr(double pixel) {
        return tRange1 + ((pixel - 1.0) / rsr2x);
    }

    // Convert pixel number to range (1 is first pixel)
    public double pix2range(double pixel) {
        return Constants.lightSpeed * pix2tr(pixel);
    }

    // Convert range time to pixel number (1 is first pixel)
    public double tr2pix(double rangeTime) {
        return 1.0 + (rsr2x * (rangeTime - tRange1));
    }

    /*---  AZIMUTH CONVERSIONS ---*/

    // Convert line number to azimuth time (1 is first line)
    public double line2ta(double line) {
        return tAzi1 + ((line - 1.0) / PRF);
    }

    // Convert azimuth time to line number (1 is first line)
    public double ta2line(double azitime) {
        return 1.0 + PRF * (azitime - tAzi1);
    }

    /*--- Getters and setters for Encapsulation ----*/
    public double getRadarWavelength() {
        return radar_wavelength;
    }

    public Point getApproxRadarCentreOriginal() {
        return approxRadarCentreOriginal;
    }

    public GeoPos getApproxGeoCentreOriginal() {
        return approxGeoCentreOriginal;
    }

    public Point getApproxXYZCentreOriginal() {
        return approxXYZCentreOriginal;
    }

    public Window getCurrentWindow() {
        return currentWindow;
    }

    public double getPRF() {
        return PRF;
    }

    public double getAzimuthBandwidth() {
        return azimuthBandwidth;
    }

    public int getCoarseOffsetP() {
        return coarseOffsetP;
    }

    public double gettRange1() {
        return tRange1;
    }

    public void settRange1(double tRange1) {
        this.tRange1 = tRange1;
    }

    public double getRangeBandwidth() {
        return rangeBandwidth;
    }

    public void setRangeBandwidth(double rangeBandwidth) {
        this.rangeBandwidth = rangeBandwidth;
    }

    public double getRsr2x() {
        return rsr2x;
    }

    public void setRsr2x(double rsr2x) {
        this.rsr2x = rsr2x;
    }

    public void setCoarseOffsetP(int offsetP) {
        this.coarseOffsetP = offsetP;
    }

    public void setCoarseOffsetL(int offsetL) {
        this.coarseOffsetL = offsetL;
    }

    public int getMlAz() {
        return mlAz;
    }

    public void setMlAz(int mlAz) {
        this.mlAz = mlAz;
    }

    public int getMlRg() {
        return mlRg;
    }

    public void setMlRg(int mlRg) {
        this.mlRg = mlRg;
    }


    public class Doppler {

        // doppler
        // private static double[] f_DC; // TODO
        boolean f_DC_const_bool;
        double f_DC_a0;                // constant term Hz
        double f_DC_a1;                // linear term Hz/s
        double f_DC_a2;                // quadratic term Hz/s/s
        double f_DC_const;

        Doppler() {
            f_DC_const_bool = false;
            f_DC_a0 = 0;
            f_DC_a1 = 0;
            f_DC_a2 = 0;
            f_DC_const = 0;
        }

        public double getF_DC_a0() {
            return f_DC_a0;
        }

        public double getF_DC_a1() {
            return f_DC_a1;
        }

        public double getF_DC_a2() {
            return f_DC_a2;
        }

        public boolean isF_DC_const() {
            return f_DC_const_bool;
        }

        public double getF_DC_const() {
            return f_DC_const;
        }

//        public void setF_DC_const(boolean f_DC_const) {
//            this.f_DC_const_bool = f_DC_const;
//        }

        /*--- DOPPLER HELPER FUNCTIONS ---*/

        // critical value!
        private double maximumDopplerChange() {
            final double percent = 0.30; // 30% ~ 100 Hz or so for ERS
            return percent * Math.abs(PRF - azimuthBandwidth);
        }

        // actual doppler change
        private double actualDopplerChange() {
            final double slcFdc_p0   = pix2fdc(currentWindow.pixlo);
            final double slcFdc_p05 = computFdc_const();
            final double slcFdc_pN   = pix2fdc(currentWindow.pixhi);

            return Math.max(Math.abs(slcFdc_p0 - slcFdc_p05), Math.abs(slcFdc_p0 - slcFdc_pN));
        }

        private double computFdc_const() {
            return pix2fdc((currentWindow.pixhi - currentWindow.pixlo) / 2);
        }

        private void checkConstant() {

            if (doppler.actualDopplerChange() < doppler.maximumDopplerChange()) {
                this.f_DC_const_bool = true;
            } else if (this.f_DC_a1 < org.jdoris.core.Constants.EPS && this.f_DC_a2 < org.jdoris.core.Constants.EPS) {
                this.f_DC_const_bool = true;
            }

            if (f_DC_const_bool) {
                f_DC_const = computFdc_const();
            }

        }

        // Convert range pixel to fDC (1 is first pixel, can be ovs)
        public double pix2fdc(double pixel) {
            final double tau = (pixel - 1.0) / (rsr2x / 2.0);// two-way time
            return f_DC_a0 + (f_DC_a1 * tau) + (f_DC_a2 * Math.pow(tau, 2));
        }

    }


}