package edu.stanford.rsl.tutorial.JuliaG;

import ij.ImageJ;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;

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
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid1D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid2D;
import edu.stanford.rsl.conrad.opencl.OpenCLUtil;

public class OpenCLGridAdd {
	
	public static void main (String [] args) throws IOException{
		//phantom anlegen
		BIO phantom1 = new BIO(512,512);
		new ImageJ();
		phantom1.show ("Phantom1");
		BIO phantom2 = new BIO(512,512);
		
		//allocate first grid
		OpenCLGrid2D g1 = new OpenCLGrid2D(phantom1);
		//OpenCLGrid2D g1 = new OpenCLGrid2D (new Grid2D (phantom1.getSize()[0], phantom1.getSize()[1]));
		
		//allocate second grid
		OpenCLGrid2D g2 = new OpenCLGrid2D(phantom2);
		//OpenCLGrid2D g2 = new OpenCLGrid2D (new Grid2D (phantom2.getSize()[0], phantom2.getSize()[1]));
		
		//allocate resulting grid: g3=g1+g2
		OpenCLGrid2D g3 = new OpenCLGrid2D (new Grid2D (phantom1.getSize()[0], phantom1.getSize()[1]));
		
		//float [] imgSize = new float [] {phantom1.getSize()[0],phantom1.getSize()[1]};
		
		//create the context
		CLContext context = OpenCLUtil.getStaticContext();
		
		//get fastest device from contest
		CLDevice device = context.getMaxFlopsDevice();
		
		//create the command queue
		CLCommandQueue commandQueue = device.createCommandQueue();
		
		//Load and compile the cl-code and create the kernel function
		InputStream is = OpenCLGridAdd.class.getResourceAsStream("kerneladd.cl");
		CLProgram program = context.createProgram(is).build();
		CLKernel kernelFunction = program.createCLKernel ("gridAddKernel");
		
		//make sure OpenCL is turned on / and things are on the device
		g1.getDelegate().prepareForDeviceOperation();
		g1.getDelegate().getCLBuffer().getBuffer().rewind();
		
		// Grid 2
		g2.getDelegate().prepareForDeviceOperation();
		g2.getDelegate().getCLBuffer().getBuffer().rewind();
		
		//Grid 3
		g3.getDelegate().prepareForDeviceOperation();
		
		//write memory on the GPU
		commandQueue
		.putWriteBuffer(g1.getDelegate().getCLBuffer(),true)
		.putWriteBuffer(g2.getDelegate().getCLBuffer(),true)
		.putWriteBuffer(g3.getDelegate().getCLBuffer(), true)
		.finish();
		
		//write kernel parameters
		kernelFunction.rewind();
		kernelFunction
		.putArg(g1.getDelegate().getCLBuffer())
		.putArg(g2.getDelegate().getCLBuffer())
		.putArg(g3.getDelegate().getCLBuffer())
		.putArg(phantom1.getSize()[0])
		.putArg(phantom1.getSize()[1]);
		
		//local worksize anlegen
		int localWorkSize = 32;
		
		//global worksize
		int globalWorkSize = OpenCLUtil.roundUp(localWorkSize, phantom1.getSize()[0]*phantom1.getSize()[1]);
		
		//execute kernel
		commandQueue.put1DRangeKernel(kernelFunction, 0, globalWorkSize, localWorkSize).finish();
		g3.getDelegate().notifyDeviceChange();
		
		new Grid2D(g3).show("ergebnis");
	}
}
