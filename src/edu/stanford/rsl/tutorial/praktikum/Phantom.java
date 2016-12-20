package edu.stanford.rsl.tutorial.praktikum;

import ij.ImageJ;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.InterpolationOperators;
import edu.stanford.rsl.conrad.data.numeric.NumericPointwiseOperators;

public class Phantom extends Grid2D {
	public Phantom(int width, int height, double [] spacing) {
		super(width, height);
		
		this.setSpacing(spacing);
		double [] origin = new double[2];
		origin[0] = -(width-1)*spacing[0]/2;
		origin[1] = -(height-1)*spacing[1]/2;
		this.setOrigin(origin);
		System.out.println(origin[0]);
		System.out.println(origin[1]);
		
		/*// build square
		int edgeLength = width/4;
		int squareCenterX = width/2-edgeLength/6;
		int squareCenterY = height/2-edgeLength/2;
		for(int row = squareCenterY-edgeLength; row<squareCenterY+edgeLength; row++){
			for (int col = squareCenterX-edgeLength; col < squareCenterX+edgeLength; col++){
				this.setAtIndex(col,row, (.2f));
			}
		}
		
		// ellipse
		long ellipseCenterX = width/2+width/8;
		//System.out.println(ellipseCenterX);
		long ellipseCenterY = height/2-height/8;
		//System.out.println(ellipseCenterY);
		long axisA = width/10;
		long axisB = height/5;
		//System.out.println(axisA);
		//System.out.println(axisB);
		for (int row = (int) (ellipseCenterY-axisB); row < ellipseCenterY+axisB; row++){
			for (int col = (int) (ellipseCenterX-axisA); col < ellipseCenterX+axisA; col++) {	
				if (((col-ellipseCenterX)*(col-ellipseCenterX)*axisB*axisB)+((row-ellipseCenterY)*(row-ellipseCenterY)*axisA*axisA) <= axisA*axisA*axisB*axisB){
					this.setAtIndex(col,row, .7f);
				}
			}
		}*/
		
		// circle
		int circleCenterX = width/2;//+width/8;
		int circleCenterY = height/2;//+height/10;
		int radius = width/5;
		for (int row = circleCenterY-radius; row < circleCenterY+radius; row++){
			for (int col = circleCenterX-radius; col < circleCenterX+radius; col++) {
				if (((col-circleCenterX)*(col-circleCenterX))+((row-circleCenterY)*(row-circleCenterY)) <= radius*radius){
					this.setAtIndex(col,row, .5f);
				}
			}
		}
		
	}

	public static void main (String[] args){
		
		new ImageJ();
		double [] spacing = {1,1};
		Phantom a = new Phantom(512,512,spacing);
		System.out.println(a.getAtIndex(230,150));
		a.show();
		System.out.println("Sum of phantom: "+ NumericPointwiseOperators.sum(a));
		System.out.println("Max of phantom: "+ NumericPointwiseOperators.max(a));
		System.out.println("Min of phantom: "+ NumericPointwiseOperators.min(a));
		System.out.println("Mean of phantom: "+ NumericPointwiseOperators.mean(a));
		System.out.println(InterpolationOperators.interpolateLinear(a, 250, 300));
		}
	}

