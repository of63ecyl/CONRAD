package edu.stanford.rsl.tutorial.praktikum;

import edu.stanford.rsl.conrad.data.numeric.Grid2D;

public class FanBeam {
	
	//spacing - mm per pixel
	private double detectorSpacing;
	//number of pixels on the detector
	private int numDetectorElements;
	//detector length in [mm] 
	private double detectorLength;
	//increment of rotation angle beta between two projections in [deg]
	private double betaIncrement;
	//number of acquired projections
	private int numProjs;
	//source to isocenter distance d_si
	private double d_si;
	//source to detector distance d_sd
	private double d_sd;
	//max rotation angle beta in [deg]
	private double maxBeta;
	
	public void setSinogramParams(Grid2D sino, double d_si,double d_sd, double maxRot)
	{
		this.d_si = d_si;
		this.d_sd = d_sd;
		this.numProjs = sino.getHeight();
		this.numDetectorElements = sino.getWidth();
		this.detectorSpacing = sino.getSpacing()[0];
		this.detectorLength = detectorSpacing*numDetectorElements;
		
		double halfFanAngle = Math.atan((detectorLength/2.0) / d_sd);
		System.out.println("Half fan angle: " + halfFanAngle*180.0);
		this.maxBeta = maxRot + 2.0*halfFanAngle;
		this.betaIncrement = maxBeta /(double) numProjs;
		System.out.println("Short-scan range: " + maxBeta*180);
		
	}
	

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
