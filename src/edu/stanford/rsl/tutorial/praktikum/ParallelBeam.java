package edu.stanford.rsl.tutorial.praktikum;

import ij.ImageJ;

import java.util.ArrayList;

import edu.stanford.rsl.conrad.data.numeric.Grid1D;
import edu.stanford.rsl.conrad.data.numeric.Grid1DComplex;
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

public class ParallelBeam {
	
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
	
	public Grid2D backprojectPixelDriven(Grid2D sino, int imageSizeX, int imageSizeY, double [] spacing) {
		
		// number of rows in sinogram
		int maxThetaIndex = sino.getSize()[1];
		
		// angle step size [deg]
		double deltaTheta = sino.getSpacing()[1];
		
		// number of columns in sinogram (in pixels)
		int maxSIndex = sino.getSize()[0];
		
		// size of detector bin [mm]
		double deltaS = sino.getSpacing()[0];
		
		// size of whole detector [mm]
		double maxS = (maxSIndex-1) * deltaS;
		
		// allocating empty output image
		Grid2D grid = new Grid2D(imageSizeX, imageSizeY);
		grid.setSpacing(spacing[0], spacing[1]);
		// set origin to the center of the output image
		grid.setOrigin(-(imageSizeX*grid.getSpacing()[0])/2, -(imageSizeY*grid.getSpacing()[1])/2);
		
		// loop over the projection angles
		for (int i = 0; i < maxThetaIndex; i++) {
			// compute actual value for theta. Convert from deg to rad
			double theta = (deltaTheta * i * Math.PI)/180;
			// pre-compute sine and cosines for faster computation
			double cosTheta = Math.cos(theta);
			double sinTheta = Math.sin(theta);
			// get detector direction vector (direction of ray shot)
			//SimpleVector dirDetector = new SimpleVector(sinTheta,cosTheta);
			SimpleVector dirDetector = new SimpleVector(cosTheta,sinTheta);
			// loops over the image grid
			for (int x = 0; x < imageSizeX; x++) {
				for (int y = 0; y < imageSizeY; y++) {
					// compute world coordinate of current pixel
					double[] w = grid.indexToPhysical(x, y);
					// wrap into vector
					SimpleVector pixel = new SimpleVector(w[0], w[1]);
					//  project pixel onto detector
					double s = SimpleOperators.multiplyInnerProd(pixel, dirDetector);
					// compute detector element index from world coordinates
					s += maxS/2; // [mm]
					s /= deltaS; // [GU]
					// get detector grid
					Grid1D subgrid = sino.getSubGrid(i);
					// check detector bounds, continue if out of array
					if (subgrid.getSize()[0] <= s + 1
							||  s < 0)
						continue;
					// get interpolated value
					float val = InterpolationOperators.interpolateLinear(subgrid, s);
					// sum value to sinogram
					grid.addAtIndex(x, y, val);
				}

			}
		}
		return grid;
	}
	
	public Grid1D rampFiltering(Grid1D sinogram, double detectorSpacing){
		
		float detectorSpacingFloat = (float) detectorSpacing;
		
		// Initialize the ramp filter
		// Define the filter in the spatial domain on the full padded size!
		Grid1DComplex ramp = new Grid1DComplex(sinogram.getSize()[0]);
		
		int paddedSize = ramp.getSize()[0];
		
		float frequencySpacing = 1.f / (detectorSpacingFloat * paddedSize);
		float maxFrequency = paddedSize/2 * frequencySpacing;
		
		for (int i = 0; i < paddedSize/2; i++)
		{
			float curFrequency = i * frequencySpacing;
			ramp.setRealAtIndex(i, curFrequency);
			//ramp.setImagAtIndex(i, curFrequency);
			ramp.setImagAtIndex(i, 0);

			
			ramp.setRealAtIndex(i+paddedSize/2, maxFrequency - curFrequency);
			//ramp.setImagAtIndex(i+paddedSize/2, maxFrequency - curFrequency);
			ramp.setImagAtIndex(i+paddedSize/2, 0);
		}
		
		//ramp.show("The Ramp Filter");
		
		Grid1DComplex sinogramF = new Grid1DComplex(sinogram,true);
		// TODO: Transform the input sinogram signal into the frequency domain
		sinogramF.transformForward();
		
		// TODO: Multiply the ramp filter with the transformed sinogram
		for(int p = 0; p < sinogramF.getSize()[0]; p++)
		{
			sinogramF.multiplyAtIndex(p, ramp.getRealAtIndex(p), ramp.getImagAtIndex(p));
		}
		
		// TODO: Backtransformation
		sinogramF.transformInverse();
		
		// Crop the image to its initial size
		Grid1D ret = new Grid1D(sinogram);
		ret = sinogramF.getRealSubGrid(0, sinogram.getSize()[0]);
				
		return ret;
	}
	
	public Grid1D ramLakFiltering(Grid1D sinogram, double detectorSpacing){
		
		float detectorSpacingFloat = (float) detectorSpacing;
		
		// Initialize the ramp filter
		// Define the filter in the spatial domain on the full padded size!
		Grid1DComplex kernel = new Grid1DComplex(sinogram.getSize()[0]);
		
		int paddedSize = kernel.getSize()[0];
		
		float frequencySpacing = 1.f / (detectorSpacingFloat * paddedSize);
		float maxFrequency = paddedSize/2 * frequencySpacing;
		
			
		final float odd = - 1.0f / (float) Math.pow(Math.PI, 2);
		// TODO: implement the ram-lak filter in the spatial domain 
		kernel.setAtIndex(0, 0.25f);
		for (int i = 1; i < paddedSize/2; i++)
		{
			if (1 == (i%2))
			{
				kernel.setAtIndex(i, odd / (float)Math.pow(i, 2));
			}
		}
		for (int i = paddedSize/2; i < paddedSize; i++)
		{
			final float tmp = paddedSize - i;
			if (1 == (i%2))
			{
				kernel.setAtIndex(i, odd / (float)Math.pow(tmp, 2));
			}
		}
		
		// TODO: Transform ramp filter into frequency domain
		kernel.transformForward();
		//kernel.show("The kernel Filter");
		
		
		Grid1DComplex sinogramF = new Grid1DComplex(sinogram,true);
		// TODO: Transform the input sinogram signal into the frequency domain
		sinogramF.transformForward();
		
		// TODO: Multiply the kernel filter with the transformed sinogram
		for(int p = 0; p < sinogramF.getSize()[0]; p++)
		{
			sinogramF.multiplyAtIndex(p, kernel.getRealAtIndex(p), kernel.getImagAtIndex(p));
		}
		
		// TODO: Backtransformation
		sinogramF.transformInverse();
		
		// Crop the image to its initial size
		Grid1D ret = new Grid1D(sinogram);
		ret = sinogramF.getRealSubGrid(0, sinogram.getSize()[0]);
				
		return ret;
	}
	
	
	public static void main(String[] args){
		new ImageJ();
		double [] spacing = {1,1};
		Phantom phantom = new Phantom(512,512,spacing);
		int sizeX = phantom.getSize()[0];
		int sizeY = phantom.getSize()[1];
		phantom.show("The phantom");
		
		ParallelBeam parallel = new ParallelBeam();
		
		// size of the phantom	
		double angularRange = 180; 	
		// number of projection images	
		int projectionNumber = 180;	
		// detector size in pixel
		float detectorSize = 512; 
		// size of a detector Element [mm]
		double detectorSpacing = 1.0f;
				
				
		Grid2D sino = parallel.projectRayDriven(phantom, projectionNumber, detectorSpacing, detectorSize, angularRange);		
		sino.show("The Unfiltered Sinogram");
		
		// Reconstruct the object with the information in the sinogram	
		Grid2D recoUnfiltered = parallel.backprojectPixelDriven(sino, sizeX, sizeY, spacing);
		recoUnfiltered.show("Unfiltered Reconstruction");
		
		
		// Ramp Filtering
		Grid2D rampFilteredSinogram = new Grid2D(sino);
		for (int theta = 0; theta < sino.getSize()[1]; ++theta) 
		{
			// Filter each line of the sinogram independently
			Grid1D tmp = parallel.rampFiltering(sino.getSubGrid(theta), detectorSpacing);
			
			for(int i = 0; i < tmp.getSize()[0]; i++)
			{
				rampFilteredSinogram.putPixelValue(i, theta, tmp.getAtIndex(i));
			}
		}
		
		rampFilteredSinogram.show("The Ramp Filtered Sinogram");
		
		// Reconstruct the object with the information in the Ramp filtered sinogram	
		Grid2D recoRampFiltered = parallel.backprojectPixelDriven(rampFilteredSinogram, sizeX, sizeY, spacing);
		recoRampFiltered.show("Ramp Filtered Reconstruction");
		
		
		// RamLak Filtering
		Grid2D ramLakFilteredSinogram = new Grid2D(sino);
		for (int theta = 0; theta < sino.getSize()[1]; ++theta) //sino.getSize()[1]
		{
			// Filter each line of the sinogram independently
			Grid1D tmp = parallel.ramLakFiltering(sino.getSubGrid(theta), detectorSpacing);
			
			for(int i = 0; i < tmp.getSize()[0]; i++)
			{
				ramLakFilteredSinogram.putPixelValue(i, theta, tmp.getAtIndex(i));
			}
		}
		
		ramLakFilteredSinogram.show("The RamLak Filtered Sinogram");
		
		// Reconstruct the object with the information in the Ramp filtered sinogram	
		Grid2D recoRamLakFiltered = parallel.backprojectPixelDriven(ramLakFilteredSinogram, sizeX, sizeY, spacing);
		recoRamLakFiltered.show("RamLak Filtered Reconstruction");
	}

}