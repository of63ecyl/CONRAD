package edu.stanford.rsl.tutorial.praktikum;

import ij.ImageJ;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid2D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGridOperators;
import edu.stanford.rsl.conrad.data.numeric.NumericPointwiseOperators;

public class OpenCLExercise {
	
	public OpenCLGrid2D convertGrid(Grid2D input){
		OpenCLGrid2D output = new OpenCLGrid2D(input);
		return output;
	}
	
	public void addGPU(OpenCLGrid2D input){
		for(int i=0;i<10000;i++){
			OpenCLGridOperators.getInstance().addBy(input,input);
		}
	}
	
	public void addCPU(Grid2D input){
		for(int i=0;i<10000;i++){
			NumericPointwiseOperators.addBy(input, input);
		}
	}

	public static void main(String[] args) {
		new ImageJ();
		OpenCLExercise obj = new OpenCLExercise();
		double [] spacing = {1,1};
		Phantom a = new Phantom(512,512,spacing);
		a.show();
		
		final long timeStartCPU = System.currentTimeMillis(); 
        obj.addCPU(a);
        final long timeEndCPU = System.currentTimeMillis(); 
        System.out.println("Verlaufszeit CPU: " + (timeEndCPU - timeStartCPU) + " Millisek.");
        a.show();
        
        Phantom c = new Phantom(512,512,spacing);
		c.show();
        
        OpenCLGrid2D b = obj.convertGrid(c);
        final long timeStartGPU = System.currentTimeMillis(); 
        obj.addGPU(b);
        final long timeEndGPU = System.currentTimeMillis(); 
        System.out.println("Verlaufszeit GPU: " + (timeEndGPU - timeStartGPU) + " Millisek.");
        b.show();

	}

}
