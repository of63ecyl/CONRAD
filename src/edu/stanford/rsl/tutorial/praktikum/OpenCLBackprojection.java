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
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLProgram;
import com.jogamp.opencl.CLImageFormat.ChannelOrder;
import com.jogamp.opencl.CLImageFormat.ChannelType;
import com.jogamp.opencl.CLMemory.Mem;

import edu.stanford.rsl.conrad.data.numeric.Grid1D;
import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.opencl.OpenCLGrid2D;
import edu.stanford.rsl.conrad.opencl.OpenCLUtil;

public class OpenCLBackprojection {
	
	public static Grid2D reconstructionCL(int worksize, int[] size, float[] spacing, double deltaS, float maxS, int maxTheta, double deltaTheta, Grid2D rampFilteredSinogram) throws IOException {
		
		float deltaSFl = (float)deltaS;
		float deltaThetaFl = (float)deltaTheta;
		
		OpenCLExercise obj = new OpenCLExercise();
		OpenCLGrid2D sinoCL = obj.convertGrid(rampFilteredSinogram);
		// allocate the resulting grid
  		OpenCLGrid2D result = new OpenCLGrid2D(new Grid2D(size[0],size[1]));
  		
		
		float[] imgSize = new float[size.length];
  		imgSize[0] = size[0];
  		imgSize[1] = size[1];
  		
  		// Create the context
  		CLContext context = OpenCLUtil.getStaticContext();
  		
  		// Get the fastest device from context
  		CLDevice device = context.getMaxFlopsDevice();
  		
  		// Create the command queue
  		CLCommandQueue commandQueue = device.createCommandQueue();
  		
  		//InputStream is = OpenCLGridTest.class
  		// Load and compile the cl-code and create the kernel function
  		InputStream is = OpenCLBackprojection.class.getResourceAsStream("openCLBackprojector.cl");
  		
  		CLProgram program = context.createProgram(is).build();
		
  		CLKernel kernelFunction = program.createCLKernel("backprojectorKernel");
  		
  		
  		// Create the OpenCL Grids and set their texture
  		
  		// Grid1
  		CLBuffer<FloatBuffer> sinoCLImgSize = context.createFloatBuffer(imgSize.length, Mem.READ_ONLY);
  		sinoCLImgSize.getBuffer().put(imgSize);
  		sinoCLImgSize.getBuffer().rewind();
  		
  		// Create the CLBuffer for the grids
  		CLImageFormat format = new CLImageFormat(ChannelOrder.INTENSITY, ChannelType.FLOAT);
  		
  		// make sure OpenCL is turned on / and things are on the device
  		sinoCL.getDelegate().prepareForDeviceOperation();
  		sinoCL.getDelegate().getCLBuffer().getBuffer().rewind();
  		
  		// Create and set the texture
  		CLImage2d<FloatBuffer> sinoCLTex = null;
  		sinoCLTex = context.createImage2d(sinoCL.getDelegate().getCLBuffer().getBuffer(), size[0], size[1], format, Mem.READ_ONLY);
  		sinoCL.getDelegate().release();
  		sinoCL.getDelegate().getCLBuffer().getBuffer().rewind();
  		
  		
  		
  		// Grid3
  		result.getDelegate().prepareForDeviceOperation();
  		result.getDelegate().getCLBuffer().getBuffer().rewind();
  		
  		float [] imsize = new float[(int)(imgSize[0]*imgSize[1])];
  		CLBuffer<FloatBuffer> result2 = context.createFloatBuffer((int)(imgSize[0]*imgSize[1]), Mem.WRITE_ONLY);
  		result2.getBuffer().put(imsize).rewind();
  		
  		// Write memory on the GPU 
  		commandQueue
  			.putWriteImage(sinoCLTex, true) // writes the first texture
  			.putReadBuffer(result.getDelegate().getCLBuffer(), true) // writes the third image buffer
  			.putWriteBuffer(sinoCLImgSize, true)
  			.finish();
  		
  		// Write kernel parameters
  		kernelFunction.rewind();
  		kernelFunction
  		.putArg(sinoCLTex)
  		.putArg(result2)
  		.putArg(sinoCLImgSize)
  		.putArg(maxTheta)
  		.putArg(maxS)
  		.putArg(deltaSFl)
  		.putArg(deltaThetaFl)
  		.putArg(spacing[0])
  		.putArg(spacing[1]);		
  				
	
  		
  		// Check correct work group sizes
  		int bpBlockSize[] = {worksize,worksize};
  		int maxWorkGroupSize = device.getMaxWorkGroupSize();
  		int[] realLocalSize = new int[] {Math.min((int)Math.pow(maxWorkGroupSize, 1/2.0),
  				bpBlockSize[0]),Math.min((int)Math.pow(maxWorkGroupSize, 1/2.0), bpBlockSize[1])};
  		
  		// rounded up to the nearest multiple of localWorkSize
  		int[] globalWorkSize = new int[]{OpenCLUtil.roundUp(realLocalSize[0], (int)imgSize[0]), OpenCLUtil.roundUp(realLocalSize[1],(int)imgSize[1])};
  		
  		// execute kernel
  		commandQueue.putWriteImage(sinoCLTex,true).finish();
  		commandQueue.put2DRangeKernel(kernelFunction, 0, 0, globalWorkSize[0], globalWorkSize[1], realLocalSize[0], realLocalSize[1]).finish();
  		commandQueue.putReadBuffer(result2,true).finish();
  		
  		//lesen
  		result2.getBuffer().get(imsize);
  		//result2.getDelegate().notifyDeviceChange();
  		
  		Grid2D resultGrid = new Grid2D(imsize,size[0],size[1]);
  		return resultGrid;

	}
	
	public static void main(String[] args){
		
		new ImageJ();
		
		// Size of the grid
		int[] size = new int[] {128,128};
		double [] spacing = {1,1};
		float [] spacingConv = {1,1};
		       
        
		Phantom phantom = new Phantom(size[0],size[1],spacing);
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
	
		
		// Ramp Filtering
		Grid2D rampFilteredSinogram = new Grid2D(sino);
		for (int theta = 0; theta < sino.getSize()[1]; ++theta)  //sino.getSize()[1]; 
		{
			// Filter each line of the sinogram independently
			Grid1D tmp = parallel.rampFiltering(sino.getSubGrid(theta), detectorSpacing);
			
			for(int i = 0; i < tmp.getSize()[0]; i++)
			{
				rampFilteredSinogram.putPixelValue(i, theta, tmp.getAtIndex(i));
			}
		}
		
		rampFilteredSinogram.show("The Ramp Filtered Sinogram");
		
		Grid2D recon;
		try {
			recon = reconstructionCL(32, size, spacingConv, detectorSpacing, detectorSize, projectionNumber, angularRange, rampFilteredSinogram);
			recon.show();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
