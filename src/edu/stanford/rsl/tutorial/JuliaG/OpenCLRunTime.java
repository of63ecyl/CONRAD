package edu.stanford.rsl.tutorial.JuliaG;

import ij.ImageJ;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.Date;

import edu.stanford.rsl.tutorial.JuliaG.BIO;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLImage2d;
import com.jogamp.opencl.CLImageFormat;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opencl.CLImageFormat.ChannelOrder;
import com.jogamp.opencl.CLImageFormat.ChannelType;
import com.jogamp.opencl.CLMemory.Mem;

import edu.stanford.rsl.conrad.data.generic.complex.OpenCLGridTest;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.NumericPointwiseOperators;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid1D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid2D;
import edu.stanford.rsl.conrad.opencl.OpenCLUtil;

public class OpenCLRunTime {
	
	public static void main (String [] args) throws IOException{
		new ImageJ();
		
		//phantom anlegen
		BIO phantom1 = new BIO(512,512);
		final long timeStart = System.currentTimeMillis();
		for (int i = 0; i<1000; i++){
			NumericPointwiseOperators.addBy(phantom1, phantom1);
		}
		final long timeEnd = System.currentTimeMillis();
		System.out.println("Laufzeit CPU: " + (timeEnd - timeStart) + " Millisek.");
		phantom1.show();
		
		
		//allocate first grid
		OpenCLGrid2D g1 = new OpenCLGrid2D(phantom1);
		final long timeStart1 = System.currentTimeMillis();
		for (int i = 0; i<1000; i++){
		g1.getGridOperator().addBy(g1, g1);
		}
		final long timeEnd1 = System.currentTimeMillis();
		System.out.println("Laufzeit GPU: " + (timeEnd1 - timeStart1) + " Millisek.");
		g1.show();
		
	}
}