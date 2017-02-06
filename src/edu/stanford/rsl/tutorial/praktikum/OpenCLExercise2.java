package edu.stanford.rsl.tutorial.praktikum;

import ij.ImageJ;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLImage2d;
import com.jogamp.opencl.CLImageFormat;
import com.jogamp.opencl.CLImageFormat.ChannelOrder;
import com.jogamp.opencl.CLImageFormat.ChannelType;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLMemory.Mem;
import com.jogamp.opencl.CLProgram;

import edu.stanford.rsl.conrad.data.generic.complex.OpenCLGridTest;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid2D;
import edu.stanford.rsl.conrad.opencl.OpenCLUtil;

public class OpenCLExercise2 {

	public static void main(String[] args){
		OpenCLExercise obj = new OpenCLExercise();
		
		// Size of the grid
		int[] size = new int[] {128,128};
		double [] spacing = {1,1};
		
		//allocate first grid
		Phantom helpG1 = new Phantom(size[0],size[1],spacing);
		helpG1.show();
        OpenCLGrid2D g1 = obj.convertGrid(helpG1);
        
        //allocate second grid
        Phantom2 helpG2 = new Phantom2(size[0],size[1],spacing);
  		helpG2.show();
  		OpenCLGrid2D g2 = obj.convertGrid(helpG2);
  		
  		// allocate the resulting grid
  		OpenCLGrid2D g3 = new OpenCLGrid2D(new Grid2D(size[0],size[1]));
  		
  		float[] imgSize = new float[size.length];
  		imgSize[0] = size[0];
  		imgSize[1] = size[1];
  		
  		// Create the context
  		CLContext context = OpenCLUtil.getStaticContext();
  		
  		// Get the fastest device from context
  		CLDevice device = context.getMaxFlopsDevice();
  		
  		// Create the command queue
  		CLCommandQueue commandQueue = device.createCommandQueue();
  		
  		// Load and compile the cl-code and create the kernel function
  		InputStream is = OpenCLGridTest.class.getResourceAsStream("openCLGridAdd.cl");
  		CLProgram program = null;
		try {
			program = context.createProgram(is).build();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
  		CLKernel kernelFunction = program.createCLKernel("gridAddKernel");
  		
  		// Create the OpenCL Grids and set their texture
  		
  		// Grid1
  		CLBuffer<FloatBuffer> gImgSize = context.createFloatBuffer(imgSize.length, Mem.READ_ONLY);
  		gImgSize.getBuffer().put(imgSize);
  		gImgSize.getBuffer().rewind();
  		
  		// Create the CLBuffer for the grids
  		CLImageFormat format = new CLImageFormat(ChannelOrder.INTENSITY, ChannelType.FLOAT);
  		
  		// make sure OpenCL is turned on / and things are on the device
  		g1.getDelegate().prepareForDeviceOperation();
  		g1.getDelegate().getCLBuffer().getBuffer().rewind();
  		
  		// Create and set the texture
  		CLImage2d<FloatBuffer> g1Tex = null;
  		g1Tex = context.createImage2d(g1.getDelegate().getCLBuffer().getBuffer(), size[0], size[1], format, Mem.READ_ONLY);
  		g1.getDelegate().release();
  		
  		// Grid 2
  		g2.getDelegate().prepareForDeviceOperation();
  		g2.getDelegate().getCLBuffer().getBuffer().rewind();
  		
  		// Create and set the texture image for second grid
  		CLImage2d<FloatBuffer> g2Tex = null;
  		g2Tex = context.createImage2d(g2.getDelegate().getCLBuffer().getBuffer(), size[0], size[1], format, Mem.READ_ONLY);
  		g2.getDelegate().release();
  		
  		// Grid3
  		g3.getDelegate().prepareForDeviceOperation();
  		
  		// Write memory on the GPU 
  		commandQueue
  			.putWriteImage(g1Tex, true) // writes the first texture
  			.putWriteImage(g2Tex, true) // writes the second texture
  			.putWriteBuffer(g3.getDelegate().getCLBuffer(), true) // writes the third image buffer
  			.putWriteBuffer(gImgSize, true)
  			.finish();
  		
  		// Write kernel parameters
  		kernelFunction.rewind();
  		kernelFunction
  		.putArg(g1Tex)
  		.putArg(g2Tex)
  		.putArg(g3.getDelegate().getCLBuffer())
  		.putArg(gImgSize);
  		
  		// Check correct work group sizes
  		int bpBlockSize[] = {32,32};
  		int maxWorkGroupSize = device.getMaxWorkGroupSize();
  		int[] realLocalSize = new int[] {Math.min((int)Math.pow(maxWorkGroupSize, 1/2.0),
  				bpBlockSize[0]),Math.min((int)Math.pow(maxWorkGroupSize, 1/2.0), bpBlockSize[1])};
  		
  		// rounded up to the nearest multiple of localWorkSize
  		int[] globalWorkSize = new int[]{OpenCLUtil.roundUp(realLocalSize[0], (int)imgSize[0]), OpenCLUtil.roundUp(realLocalSize[1],(int)imgSize[1])};
  		
  		// execute kernel
  		commandQueue.put2DRangeKernel(kernelFunction, 0, 0, globalWorkSize[0], globalWorkSize[1], realLocalSize[0], realLocalSize[1]).finish();
  		g3.getDelegate().notifyDeviceChange();
  		
  		new ImageJ();
  		new Grid2D(g3).show();

	}

}
