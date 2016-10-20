package edu.stanford.rsl.tutorial.praktikum;

import edu.stanford.rsl.conrad.data.numeric.Grid2D;

public class Phantom extends Grid2D {
	public Phantom(int width, int height) {
		super(width, height);
		// TODO Auto-generated constructor stub
		
		// build square
		int edgeLength = width/8;
		for(int row = edgeLength; row<width/2+edgeLength; row++){
			for (int col = edgeLength; col < width/2+edgeLength; col++){
				this.setAtIndex(col,row, (.2f));
			}
		}
		
		// circle
		int circleCenterX = width/2+width/8;
		int circleCenterY = height/2+height/4;
		int radius = width/10;
		for (int row = circleCenterY-radius; row < circleCenterY+radius; row++){
			for (int col = circleCenterX-radius; col < circleCenterX+radius; col++) {
				if (((col-circleCenterX)*(col-circleCenterX))+((row-circleCenterY)*(row-circleCenterY)) <= radius*radius){
					this.setAtIndex(col,row, .5f);
				}
			}
		}
	}

	public static void main (String[] args){
		Phantom a = new Phantom(512,512);
		a.show();
	}
}
