package edu.stanford.rsl.tutorial.praktikum;

import ij.ImageJ;

import java.util.ArrayList;

import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.InterpolationOperators;
import edu.stanford.rsl.conrad.geometry.shapes.simple.Box;
import edu.stanford.rsl.conrad.geometry.shapes.simple.PointND;
import edu.stanford.rsl.conrad.geometry.shapes.simple.StraightLine;
import edu.stanford.rsl.conrad.geometry.transforms.Transform;
import edu.stanford.rsl.conrad.geometry.transforms.Translation;
import edu.stanford.rsl.conrad.numerics.SimpleOperators;
import edu.stanford.rsl.conrad.numerics.SimpleVector;
import edu.stanford.rsl.tutorial.dmip.DMIP_ParallelBeam.RampFilterType;

public class SinogramGeneration {
	
	public Grid2D projectRayDriven(Grid2D grid, int projectionNumber, double spacing, float detectorSize, double angularRange) {
		
		int maxSIndex = (int) detectorSize;
		double maxS = detectorSize * spacing;
		int maxThetaIndex = projectionNumber;
		
		double deltaS = spacing;
		double deltaTheta = angularRange / projectionNumber;
		
		final double samplingRate = 3.d; // # of samples per pixel
		Grid2D sino = new Grid2D(new float[maxThetaIndex*maxSIndex], maxSIndex, maxThetaIndex);
		sino.setSpacing(deltaS, deltaTheta);

		// set up image bounding box in WC
		Translation trans = new Translation(
				-(grid.getSize()[0] * grid.getSpacing()[0])/2, -(grid.getSize()[1] * grid.getSpacing()[1])/2, -1);

		Box b = new Box((grid.getSize()[0] * grid.getSpacing()[0]), (grid.getSize()[1] * grid.getSpacing()[1]), 2);
		b.applyTransform(trans);

		for(int e=0; e<maxThetaIndex; ++e){
			// compute theta [rad] and angular functions.
			double theta = (deltaTheta * e * Math.PI)/180;
			double cosTheta = Math.cos(theta);
			double sinTheta = Math.sin(theta);

			for (int i = 0; i < maxSIndex; ++i) {
				// compute s, the distance from the detector edge in WC [mm]
				double s = deltaS * i - maxS / 2;
				// compute two points on the line through s and theta
				// We use PointND for Points in 3D space and SimpleVector for directions.
				PointND p1 = new PointND(s * cosTheta, s * sinTheta, .0d);
				PointND p2 = new PointND(-sinTheta + (s * cosTheta),
						(s * sinTheta) + cosTheta, .0d);
				// set up line equation
				StraightLine line = new StraightLine(p1, p2);
				// compute intersections between bounding box and intersection line.
				ArrayList<PointND> points = b.intersect(line);

				/*// only if we have intersections
				if (2 != points.size()){
					if(points.size() == 0) {
						line.getDirection().multiplyBy(-1.d);
						points = b.intersect(line);
					}*/
					if(points.size() != 2){
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
				sino.setAtIndex(i, e, (float)sum);
			}
		}
		return sino;
	}
	
	public static void main(String[] args){
		new ImageJ();
		double [] spacing = {1,1};
		Phantom phantom = new Phantom(512,512,spacing);
		phantom.show("The phantom");
		
		SinogramGeneration parallel = new SinogramGeneration();
		
		// size of the phantom	
				double angularRange = 180; 	
				// number of projection images	
				int projectionNumber = 180;	
				// angle in between adjacent projections
				double angularStepSize 	= angularRange / projectionNumber;
				// detector size in pixel
				float detectorSize = 512; 
				// size of a detector Element [mm]
				double detectorSpacing = 1.0f;
				
				
		Grid2D sino = parallel.projectRayDriven(phantom, projectionNumber, detectorSpacing, detectorSize, angularRange);		
		sino.show("The Sinogram");
	}

}
