package edu.stanford.rsl.tutorial.praktikum;

import ij.ImageJ;

import java.util.ArrayList;

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
		double betaIncrement = maxBeta / numProjs;
		System.out.println(betaIncrement);
		
		
		final double samplingRate = 3.d; // # of samples per pixel
		Grid2D fanogram = new Grid2D(new float[numProjs*numDetectorElements], numDetectorElements, numProjs);
		fanogram.setSpacing(detectorSpacing, betaIncrement);
		
		//PointND originP = new PointND(grid.getOrigin()[0],grid.getOrigin()[1], .0d);

		// set up image bounding box in WC
		Translation trans = new Translation(
				(grid.getOrigin()[0]), grid.getOrigin()[1], -1);

		Box b = new Box((grid.getSize()[0] * grid.getSpacing()[0]), (grid.getSize()[1] * grid.getSpacing()[1]), 2);
		b.applyTransform(trans);
		//System.out.println(b.getMax().toString() + b.getMin().toString());

		// iterate over angle
		for(int e=0; e<numProjs; ++e){
		//for(int e=0; e<2; ++e){
			// compute beta [rad] and angular functions.
			double beta = (betaIncrement * e * Math.PI)/180;
			double cosBeta = Math.cos(beta);
			double sinBeta = Math.sin(beta);
			
			// location of source (start in 2nd quadrant)
			PointND sourceP = new PointND (-d_si * sinBeta, d_si * cosBeta, .0d);
			System.out.println(sourceP.toString());
			SimpleVector sourceVec = new SimpleVector(sourceP.getAbstractVector());
			
			//vector between source and detector center (central ray)
			sourceVec.dividedBy(d_si).multipliedBy(-(d_sd-d_si)); 
			
			// vector in origin, pointing orthogonal to central ray (parallel to detector)
			PointND sPoint = new PointND(deltaS * cosBeta, deltaS * sinBeta, .0d);
			SimpleVector sVector = new SimpleVector(sPoint.getAbstractVector());
			SimpleVector sVectorClone = sVector.clone();
			
			SimpleVector rayVec = new SimpleVector(sourceVec);
			rayVec.subtract(sVectorClone.multipliedBy((detectorLength / 2)));
			//System.out.println(rayVec.toString());
			rayVec.multiplyElementBy(1, -1);
			//System.out.println(rayVec.toString());
			
			for (int i = 0; i < maxSIndex; ++i) { 
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
				
				
				for (double t = 0.0; t < (distance * samplingRate)-1; t++){
					current.getAbstractVector().add(integralVec);
					
					indices = grid.physicalToIndex(current.get(0), current.get(1));
					
					if (grid.getSize()[0] <= indices[0] + 1 || grid.getSize()[1] <= indices[1] + 1 || indices[0] < 0 || indices[1] < 0){
						continue;
					}
					
					sum += InterpolationOperators.interpolateLinear(grid, indices[0], indices[1]);
				}	
								

				// normalize by the number of interpolation points
				sum /= samplingRate;
				
				
				// write integral value into the sinogram.
				fanogram.setAtIndex(i, e, (float)sum);
			}
		}
		return fanogram;
	}
	

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		new ImageJ();
		double [] spacing = {1,1};
		Phantom phantom = new Phantom(512,512,spacing);
		int sizeX = phantom.getSize()[0];
		int sizeY = phantom.getSize()[1];
		phantom.show("The phantom");
		
		FanBeam fan = new FanBeam();
		
		// number of projection images	
		int projectionNumber = 180;	
		// detector size in pixel
		int detectorSize = 512; 
		// size of a detector Element [mm]
		double detectorSpacing = 1.0f;
		
		double d_si = 780;
		double d_sd = 1200;
		double halfFanAngle = Math.atan((detectorSize/2.0)/d_sd)*180;
		//double halfFanAngle = 0;
		System.out.println(halfFanAngle);
		double maxBeta = 180 + 2 * halfFanAngle;
		System.out.println(maxBeta);
		
				
				
		Grid2D fanogram = fan.projectRayDrivenFan(phantom, projectionNumber, detectorSpacing, detectorSize, maxBeta, d_si, d_sd); 
		fanogram.show("The Unfiltered Sinogram");

		// d_sd und d_si richtig beruecksichtigen
		// drehrichtung detektor??
		
	}

}
