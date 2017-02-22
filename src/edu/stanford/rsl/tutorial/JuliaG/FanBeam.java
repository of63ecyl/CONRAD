package edu.stanford.rsl.tutorial.JuliaG;

import ij.ImageJ;

import java.util.ArrayList;

import edu.stanford.rsl.conrad.data.numeric.Grid1D;
import edu.stanford.rsl.conrad.data.numeric.Grid1DComplex;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.Grid2DComplex;
import edu.stanford.rsl.conrad.data.numeric.InterpolationOperators;
import edu.stanford.rsl.conrad.data.numeric.NumericGrid;
import edu.stanford.rsl.conrad.data.numeric.NumericPointwiseOperators;
import edu.stanford.rsl.conrad.geometry.shapes.simple.Box;
import edu.stanford.rsl.conrad.geometry.shapes.simple.PointND;
import edu.stanford.rsl.conrad.geometry.shapes.simple.StraightLine;
import edu.stanford.rsl.conrad.geometry.transforms.Transform;
import edu.stanford.rsl.conrad.geometry.transforms.Translation;
import edu.stanford.rsl.conrad.numerics.SimpleOperators;
import edu.stanford.rsl.conrad.numerics.SimpleVector;
import edu.stanford.rsl.conrad.utils.FFTUtil;
import edu.stanford.rsl.tutorial.phantoms.SheppLogan;
import edu.stanford.rsl.tutorial.JuliaG.ParallelBeam;

public class FanBeam {
	//maxT, deltaT: detector length, size of dectector element
	//deltabeta, maxbeta: increment of rotation angle
	//dsi: distance source isocenter
	//dsd: distance source detector

	public static Grid2D fanogram (Grid2D grid, double spacing, double deltaT, int maxT, int deltaBeta, int maxBeta, double dsi, double dsd){
		
		double focalLength = dsi; 
		grid.setSpacing(spacing, spacing);
		//spacing von Bild mit rein in Eingabeparameter?
		grid.setOrigin(-(grid.getSize()[0]*grid.getSpacing()[0])/2, -(grid.getSize()[1]*grid.getSpacing()[1])/2);

		int betaSteps = (int)((maxBeta/deltaBeta));
		int Tsteps = (int)((maxT/deltaT));
		
		//initialize sinogram, set spacing
		Grid2D sino = new Grid2D(maxT, maxBeta);
		sino.setSpacing(deltaT, deltaBeta);
		sino.setOrigin(-(maxT-1)/2,0);
		
        
        //iterate over angels
        for (int i =0; i<betaSteps; i++){

        	//convert from deg to rad and caculate sin, cos
			float beta = (float) (deltaBeta * i);
			float cosBeta = (float) Math.cos(((2*Math.PI)/360)*beta);
			float sinBeta = (float) Math.sin(((2*Math.PI)/360)*beta);
			
			PointND sourcepoint = new PointND (-focalLength*sinBeta, focalLength*cosBeta);
			PointND isocenter = new PointND(0,0);
			
			SimpleVector detectorposition = new PointND (+(dsd-focalLength)*sinBeta, -(dsd-focalLength)*cosBeta).getAbstractVector();
			
			SimpleVector source = new SimpleVector(sourcepoint.getAbstractVector());
			
			
			for(int j = 0; j< Tsteps; j++) {
				float sum = .0f;
				double t = j*deltaT - maxT/2;
				
				SimpleVector currentposition =  detectorposition;
				PointND direction = new PointND(cosBeta, sinBeta);
			
				SimpleVector pointT = new SimpleVector(direction.getAbstractVector());
				
				currentposition.add(pointT.multipliedBy(t));

				currentposition.subtract(source);
				double distance = currentposition.normL2();
				//Schrittweite auf Linie berechnen
				currentposition.divideBy(distance);
				
				for (int k = 0; k < distance; k++) {
                    // Anfangspunkt
                    PointND begin = new PointND(sourcepoint);
                    SimpleVector sourcefix = new SimpleVector(sourcepoint.getAbstractVector());
                    // Laufe zu meinem Punkt, in oben berechneter Richtung, abhaengig von k
                    sourcefix.add(currentposition.multipliedBy(k));
                     
                    // Brauche nun Value an diesem Punkt, hierfuer muss Wert an x- und y-Koordinate berechnet werden (spacing beachten)
                    double x = (sourcefix.getElement(0)-grid.getOrigin()[0])/grid.getSpacing()[0];
                    double y = (sourcefix.getElement(1)-grid.getOrigin()[1])/grid.getSpacing()[1];
                     
                    // Interpolation, da Ort nicht immer direkt auf einen Pixel faellt, d.h. nicht auf einen genauen Wert
                     
                    double value = InterpolationOperators.interpolateLinear(grid, x, y);
                    // aufsummieren aller Werte ueber ganze Linie
                    sum = (float) (sum + value);
                     
                }
				sino.setAtIndex(j, i, (float)sum);
			}
        }
        return sino;
	}
	
    public static Grid2D rebinning (Grid2D fano, double dsi, double dsd){
    	//initialize sinogram, world coordinates
        Grid2D rebinned = new Grid2D (fano.getWidth(), 180);
        rebinned.setSpacing(fano.getSpacing()[0], fano.getSpacing()[1]);
        rebinned.setOrigin(fano.getOrigin()[0], fano.getOrigin()[1]);
        	
        for (int s = 0; s<rebinned.getWidth(); s++){
        	for (int theta = 0; theta<rebinned.getHeight(); theta++){
            	
            	//world coordinaten in fano berchnen
            	double [] world = rebinned.indexToPhysical(s, theta);
            	double worldTheta = world[1];//degree
            	double worldTheta_rad = Math.toRadians(worldTheta);
            	double worldS = world[0];
            	
            	//gamma, beta und t berechnen
            	double gamma = Math.asin(worldS/dsi); //rad

                double beta = worldTheta_rad-(gamma);
            	if(beta < 0) {
            		beta += 2*Math.PI;
            		if(beta > 359/360*(2*Math.PI)) {
            			beta = 359/360*(2*Math.PI);
            		}
            	}
            	if (beta >= 2*Math.PI){
            		beta -=2*Math.PI;
            	}
                
                double t = Math.tan((gamma))*dsd;
                double[] tIdx = fano.physicalToIndex(t, beta);
                
                double value = InterpolationOperators.interpolateLinear(fano, tIdx[0], Math.toDegrees(beta));

                rebinned.setAtIndex(s, theta, (float) value); //in Folien fan-o-gram in abhängigkeit von gamma und beta?
            }
        }
        return rebinned;
    }
    
//    public static Grid2D shortscan (Grid2D grid, double spacing, double deltaT, int maxT, int deltaBeta, double dsi, double dsd){
//    	double gamma_half = Math.atan((maxT/2)/(dsd)); //rad
//    	double gamma = gamma_half*2; //rad
//    	
//    	double shortscan = Math.PI+gamma;
//    }
	
	public static void main(String[] args) {
		new ImageJ();
		SheppLogan object = new SheppLogan(256);
		object.show();
		Grid2D fano = fanogram(object,1,1,400,1,200,600,800);
		fano.show("fanogram");
		Grid2D sino = rebinning (fano,600,800);
		sino.show("sinogram");
		Grid2D ramlaksino = ParallelBeam.ramlak(sino);
		Grid2D recon = ParallelBeam.backprojection(256, 1,ramlaksino, 1,1);
		recon.show();
	}
}