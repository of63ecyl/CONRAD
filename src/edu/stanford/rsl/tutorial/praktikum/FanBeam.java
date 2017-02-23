package edu.stanford.rsl.tutorial.praktikum;

import ij.ImageJ;

import java.util.ArrayList;

import edu.stanford.rsl.conrad.data.numeric.Grid1D;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.InterpolationOperators;
import edu.stanford.rsl.conrad.geometry.shapes.simple.Box;
import edu.stanford.rsl.conrad.geometry.shapes.simple.PointND;
import edu.stanford.rsl.conrad.geometry.shapes.simple.StraightLine;
import edu.stanford.rsl.conrad.geometry.transforms.Translation;
import edu.stanford.rsl.conrad.numerics.SimpleOperators;
import edu.stanford.rsl.conrad.numerics.SimpleVector;

public class FanBeam {
	
	
public Grid2D projectRayDrivenFan(Grid2D grid, int numProjs, double detectorSpacing, int numDetectorElements, double maxBeta, double d_si, double d_sd) {
		
		// detector index
		int maxSIndex = numDetectorElements;
		double detectorLength = (numDetectorElements-1) * detectorSpacing; //mm
		
		// angle index
		double deltaS = detectorSpacing;
		double betaIncrement = maxBeta / (1.0*numProjs);
		//System.out.println(betaIncrement);
		
		double d_id = d_sd - d_si; 
		
		final double samplingRate = 3.d; // # of samples per pixel
		Grid2D fanogram = new Grid2D(new float[numProjs*numDetectorElements], numDetectorElements, numProjs);
		fanogram.setSpacing(detectorSpacing, betaIncrement);
		// set origin to the center of the output image
        fanogram.setOrigin(-((numDetectorElements-1)*fanogram.getSpacing()[0])/2.0, 0.0); //-(numProjs*grid.getSpacing()[1])/2);
		
		//PointND originP = new PointND(grid.getOrigin()[0],grid.getOrigin()[1], .0d);

		// set up image bounding box in WC
		Translation trans = new Translation(
				(grid.getOrigin()[0]), grid.getOrigin()[1], -1);

		Box b = new Box((grid.getSize()[0] * grid.getSpacing()[0]), (grid.getSize()[1] * grid.getSpacing()[1]), 2);
		b.applyTransform(trans);
		//System.out.println(b.getMax().toString() + b.getMin().toString());

		// iterate over angle
		for(int e=0; e<numProjs; e++){
		//for(int e=0; e<1; ++e){
			// compute beta [rad] and angular functions.
			double beta = (betaIncrement * e * Math.PI)/180;
			double cosBeta = Math.cos(beta);
			double sinBeta = Math.sin(beta);
			
			// location of source (start in 2nd quadrant)
			PointND sourceP = new PointND (-d_si * sinBeta, d_si * cosBeta, .0d);
			//System.out.println(e);
			//System.out.println(sourceP.toString());
			SimpleVector sourceVec = new SimpleVector(sourceP.getAbstractVector());
			//System.out.println(sourceVec.toString());betaIncrement
			
			//vector between source and detector center (central ray)
			sourceVec.normalizeL2();
			//System.out.println(sourceVec.toString());
			//sourceVec.dividedBy(d_si);
			//System.out.println(sourceVec.toString());
			sourceVec.multiplyBy(d_id);
			sourceVec.multiplyBy(-1);
			//System.out.println(sourceVec.toString());
			
			// vector in origin, pointing orthogonal to central ray (parallel to detector)
			PointND sPoint = new PointND(deltaS * cosBeta, deltaS * sinBeta, .0d);
			SimpleVector sVector = new SimpleVector(sPoint.getAbstractVector());
			//System.out.println(sVector.toString());
			SimpleVector sVectorClone = sVector.clone();
			//System.out.println(sVectorClone.toString());
			
			SimpleVector rayVec = new SimpleVector(sourceVec);
			sVectorClone.multiplyBy((detectorLength / 2));
			//System.out.println(sVectorClone.toString());
			rayVec.subtract(sVectorClone); // Hier sollten wir ganz links auf dem Detektor liegen!
			//System.out.println(rayVec.toString());
			//rayVec.multiplyElementBy(1, -1);
			//System.out.println(rayVec.toString());
			
			for (int i = 0; i < maxSIndex; i++) { 
				// compute s, the distance from the detector edge in WC [mm]
				SimpleVector sVectorClone2 = sVector.clone();
				rayVec.add(sVectorClone2.multipliedBy(deltaS));
				
				PointND rayP = new PointND(rayVec.getElement(0),rayVec.getElement(1), .0d);
				//System.out.println(sourceP.toString());
				//System.out.println(rayP.toString());
				
				
				// set up line equation
				StraightLine line = new StraightLine(sourceP, rayP);
				//System.out.println(line.toString());
				// compute intersections between bounding box and intersection line.
				ArrayList<PointND> points = b.intersect(line);
				//System.out.println(points.toString());
				// only if we have intersections
					if(points.size() != 2){
						//System.out.println("angle: " + e + "   ray: " + i);
						continue;
					}
				

				PointND start = points.get(0); // [mm]
				PointND end = points.get(1);   // [mm]
				
				SimpleVector startVec = new SimpleVector(start.getAbstractVector());
				//start = inverse.transform(start);
				
				PointND current = new PointND(startVec);
				
				// compute once for start point
				double [] indices = grid.physicalToIndex(current.get(0), current.get(1));				
				double sum = InterpolationOperators.interpolateLinear(grid, indices[0], indices[1]);
				
				
				
				SimpleVector endVec = new SimpleVector(end.getAbstractVector());
				SimpleVector integralVec = SimpleOperators.subtract(endVec, startVec);
				double distance = integralVec.normL2();
				integralVec.divideBy(distance * samplingRate);
				
				//double sum = 0;
				for (int t = 0; t < (distance * samplingRate); t++){
					current.getAbstractVector().add(integralVec);
					
					indices = grid.physicalToIndex(current.get(0), current.get(1));
					//double [] spacing = {1,1};
					
					if (grid.getSize()[0] <= indices[0] + 1 || grid.getSize()[1] <= indices[1] + 1 || indices[0] < 0 || indices[1] < 0){
						continue;
					}
					
					sum += InterpolationOperators.interpolateLinear(grid, indices[0], indices[1]);
				}	
								

				// normalize by the number of interpolation points
				sum /= samplingRate;
				
				
				// write integral value into the sinogram.
				fanogram.setAtIndex(i+1, e, (float)sum);
			}
		}
		return fanogram;
	}

public Grid2D rebinning(Grid2D fanogram, int detectorSize, double detectorSpacing, double halfFanAngle, double d_si, double d_sd) {
		
		int maxSIndex = detectorSize;
		double maxS = (detectorSize) * detectorSpacing;  //(detectorSize-1) * detectorSpacing;
		int maxThetaIndex = 180;
	
		double deltaS = detectorSpacing;
		double deltaTheta = 1.0; //maxThetaIndex / projectionNumber;
		
		Grid2D sino = new Grid2D (fanogram.getWidth(), 180);
        sino.setSpacing(fanogram.getSpacing()[0], fanogram.getSpacing()[1]);
        sino.setOrigin(fanogram.getOrigin()[0], fanogram.getOrigin()[1]);
		
		for(int e=0; e<sino.getHeight(); e++){
			// compute theta [rad] and angular functions.
			double theta = (deltaTheta * e * Math.PI)/180;

			for (int i = 0; i < sino.getWidth(); i++) {
				double s_par = deltaS * i; //- maxS / 2;
				double [] s_par_world = sino.indexToPhysical(s_par, theta);
				double gamma = Math.asin(s_par_world[0]/d_si);
				double beta = theta - gamma;
				double s_fan_world = Math.tan(gamma) * d_sd;
				if(beta<0){
					//System.out.println(beta);
					gamma = -gamma;
					beta = beta - 2*gamma + Math.PI;
					s_fan_world = (Math.tan(gamma) * d_sd);
				}
				
				double [] indices = fanogram.physicalToIndex(s_fan_world, Math.toDegrees(beta));
				float curValue = InterpolationOperators.interpolateLinear(fanogram, indices[0], indices[1]);
				
				sino.setAtIndex(i, e, curValue);
			}
		}
		
		return sino;
}
	



	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new ImageJ();
		double [] spacing = {1,1};
		Phantom phantom = new Phantom(256,256,spacing);
		int sizeX = phantom.getSize()[0];
		int sizeY = phantom.getSize()[1];
		phantom.show("The phantom");
		
		FanBeam fan = new FanBeam();
		
		
		// detector size in pixel
		int detectorSize = 512; 
		// size of a detector Element [mm]
		double detectorSpacing = 1.0;
		
		double d_si = 1000.0;
		double d_sd = 1500.0;
		double halfFanAngle = Math.atan((detectorSize/2.0)/d_sd)*180;
		//double halfFanAngle = 0;
		//System.out.println(halfFanAngle);
		//double maxBeta = 180;
		double maxBeta = (180 + (2 * halfFanAngle));
		//System.out.println(maxBeta);
		// number of projection images	
		int projectionNumber = (int) maxBeta;	
		
				
				
		Grid2D fanogram = fan.projectRayDrivenFan(phantom, projectionNumber, detectorSpacing, detectorSize, maxBeta, d_si, d_sd); 
		fanogram.show("The Unfiltered Fanogram");
		
		Grid2D sinogram = fan.rebinning(fanogram, detectorSize, detectorSpacing, halfFanAngle, d_si, d_sd);
		sinogram.show("The rebinning result");
		
		ParallelBeam parallel = new ParallelBeam();
		
		// Ramp Filtering
				Grid2D rampFilteredSinogram = new Grid2D(sinogram);
				for (int theta = 0; theta < sinogram.getSize()[1]; theta++)  //sino.getSize()[1]; 
				{
					// Filter each line of the sinogram independently
					Grid1D tmp = parallel.rampFiltering(sinogram.getSubGrid(theta), detectorSpacing);
					
					for(int i = 0; i < tmp.getSize()[0]; i++)
					{
						rampFilteredSinogram.putPixelValue(i, theta, tmp.getAtIndex(i));
					}
				}
				
				rampFilteredSinogram.show("The Ramp Filtered Sinogram");
		
		Grid2D recoRampFiltered = parallel.backprojectPixelDriven(rampFilteredSinogram, sizeX, sizeY, spacing);
		recoRampFiltered.show("Ramp Filtered Reconstruction");

		
	}

}