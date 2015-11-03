package edu.stanford.rsl.tutorial.dmip;

import edu.stanford.rsl.conrad.data.numeric.Grid2D;
import edu.stanford.rsl.conrad.data.numeric.NumericPointwiseOperators;
import edu.stanford.rsl.conrad.utils.ImageUtil;
import edu.stanford.rsl.conrad.utils.VisualizationUtil;
import ij.IJ;
import ij.ImageJ;
import edu.stanford.rsl.conrad.data.numeric.InterpolationOperators;
import edu.stanford.rsl.conrad.numerics.SimpleMatrix;
import edu.stanford.rsl.conrad.numerics.SimpleOperators;
import edu.stanford.rsl.conrad.numerics.SimpleVector;
import edu.stanford.rsl.conrad.numerics.DecompositionSVD;
import edu.stanford.rsl.conrad.numerics.SimpleMatrix.InversionType;

public class ImageUndistortion{

	
	public void doImageUndstortion(){

		/////////////////////
		// 1.Preprocessing //
		/////////////////////
		// In the preprocessing part, an artificial distortion field is generated.
		// With this field, a distorted image is generated.
		
		
		// 1. Load undistorted image
		
		//adapt the paths
		int caseNo = 0;
		String filename = "/proj/i5dmip/of63ecyl/Reconstruction/CONRAD/src/edu/stanford/rsl/tutorial/dmip/frame32.jpg";
		
		if(caseNo == 0)
		{
			filename = "/proj/i5dmip/of63ecyl/Reconstruction/CONRAD/src/edu/stanford/rsl/tutorial/dmip/frame32.jpg";
		}
		else if(caseNo == 1)
		{
			filename = "/proj/i5dmip/of63ecyl/Reconstruction/CONRAD/src/edu/stanford/rsl/tutorial/dmip/undistorted.jpg";
		}else if(caseNo == 2)
		{
			filename = "/proj/i5dmip/of63ecyl/Reconstruction/CONRAD/src/edu/stanford/rsl/tutorial/dmip/frame90.jpg";
		}
				
		Grid2D image = ImageUtil.wrapImagePlus(IJ.openImage(filename)).getSubGrid(0);
		
		
		// 2. Normalize intensity values to [0,1]
		float max = NumericPointwiseOperators.max(image);
		float min = NumericPointwiseOperators.min(image);
		
		for(int i = 0; i < image.getWidth(); i++)
		{
			for(int j = 0; j < image.getHeight(); j++)
			{
				float scaledValue = (image.getAtIndex(i, j) - min) / (max-min);
				image.setAtIndex(i, j, scaledValue);
			}
		}
		image.show("Input Image");
		
		
		// 3. Make the image quadratic
		int h = image.getHeight();
		int w = image.getWidth();
		
		int imSize = Math.min(h, w);
		
		Grid2D quadraticImage = new Grid2D(imSize, imSize); 
		
		for(int i  = 0; i < image.getWidth(); i++)
		{
			for(int j = 0; j < image.getHeight(); j++)
			{
				quadraticImage.setAtIndex(i, j, image.getPixelValue(i, j));
			}
		}
		
		quadraticImage.show("Quadratic Input Image");
		
		
		// 4. Generate a grid to sample the image
		// undistorted coordinates X, Y
		Grid2D X = new Grid2D(imSize, imSize);
		Grid2D Y = new Grid2D(imSize, imSize);
		
		for(int i = 0; i < X.getWidth(); i++)
		{
			for(int j = 0; j < X.getHeight(); j++)
			{
				X.setAtIndex(i, j, i);
				Y.setAtIndex(i, j, j);
			}
		}
		
		// 5. Create an artificial elliptical distortion field (R)
		// a: spread among x-direction
		// b: spread among y-direction
		// d: (d/2) = maximal value at the radius boundary
		// NumericPointwiseOperators.subtractBy(R, (float) (maxR * 0.5)):
		// 		shifting the positive range to half positive/half negative range
		Grid2D R = new Grid2D(imSize, imSize);
		float a = 3;
		float b = 9; 
		float d = 12;
		
		int half = imSize / 2;
		
		for(int i = 0; i < R.getWidth(); i++)
		{
			for(int j = 0; j < R.getHeight(); j++)
			{
				float r = (float) (d * Math.sqrt( a * Math.pow(((X.getAtIndex(i, j) - half) / imSize) , 2)  + b * Math.pow(((Y.getAtIndex(i, j) - half) / imSize) , 2) ) ) ;
				R.setAtIndex(i, j, r);
			}
		}
		
		R.show("Distortion Field");
		Grid2D shiftedR = new Grid2D(R);
		float maxR = NumericPointwiseOperators.max(R);
		NumericPointwiseOperators.subtractBy(shiftedR, (float) (maxR * 0.5));
		
		shiftedR.show("Shifted Distortion Field");
		
		// 5. Create the distorted image coordinates:
		// distorted image coordinates = undistorted image points + artificial distortion field
		
		// distorted coordinates Xd, Yd
		Grid2D Xd = new Grid2D(X);
		Grid2D Yd = new Grid2D(Y);
		
		NumericPointwiseOperators.addBy(Xd, shiftedR);
		NumericPointwiseOperators.addBy(Yd, shiftedR);
		
		// 6. Create the distorted image
		// Resample the original input image at the distorted image points (XD,YD) to create artificial distortion
		
		Grid2D distortedImage = new Grid2D(imSize, imSize);
		for(int i = 0; i < R.getWidth(); i++)
		{
			for(int j = 0; j < R.getHeight(); j++)
			{
				float distortedValue = InterpolationOperators.interpolateLinear(image, Xd.getAtIndex(i, j), Yd.getAtIndex(i, j));
				distortedImage.setAtIndex(i, j, distortedValue);
			}
		}
		
		distortedImage.show("Distorted Image");
				
		///////////////////////////////////
		// Image Undistortion - Workflow //
		///////////////////////////////////
		
		
		// 1. Number of lattice points (this only works for symmetric images).
		// nx, ny feature points: usually provided by tracking point
		// correspondences in the phantom during the calibration step.
		// Here, the distorted and undistorted coordinates from the preprocessing
		// can be used.
		
		// Number of lattice points
		// TODO: define the number of lattice points
		// change the value of nx, ny
		int nx = 8;
		int ny = 8;
		
		// step size
		// TODO: calculate the stepsize of the lattice points 
		float fx = imSize / (nx-1.f);
		float fy = imSize / (ny-1.f);
		
		// Fill the distorted and undistorted lattice points with the 
		// grid coordinates from the preprocessing part.
		SimpleMatrix Xu2 = new SimpleMatrix(ny,nx);
		SimpleMatrix Yu2 = new SimpleMatrix(ny,nx);
		SimpleMatrix Xd2 = new SimpleMatrix(ny,nx);
		SimpleMatrix Yd2 = new SimpleMatrix(ny,nx);
		
		for(int i = 0; i < ny; i++)
		{
			for(int j = 0; j < nx; j++)
			{
				//TODO: sample the distorted and undistorted grid points at the lattice points
				Xu2.setElementValue(i, j, X.getAtIndex(i*(int)fx, j*(int)fy));
				Yu2.setElementValue(i, j, Y.getAtIndex(i*(int)fx, j*(int)fy));
				Xd2.setElementValue(i, j, Xd.getAtIndex(i*(int)fx, j*(int)fy));
				Yd2.setElementValue(i, j, Yd.getAtIndex(i*(int)fx, j*(int)fy));
			}
		}
		
		// Compute the distorted points: be aware of the fact, that the artificial deformation takes
		// place from the distorted to undistorted! 
		// In the Preprocessing: X + distortion = Xd
		// Now: We correct the distorted image to get the undistorted one!
		// Thus we have the flip the influence of the distortion field.
		
		
		
		// Compute the distorted points:
		// XD2 = XU2 + (XU2 - XD2)
		// TODO:
		Xd2 = SimpleOperators.add(Xu2,SimpleOperators.subtract(Xu2, Xd2));
		Yd2 = SimpleOperators.add(Yu2,SimpleOperators.subtract(Yu2, Yd2));
		
		
		// 2. Polynom of degree d
		// Polynom of degree d -> (d-1): extrema
		// d=0: constant (horizontal line with y-intercept a_0 -> f(x)=a_0)
		// d=1: oblique line with y-intercept a_0 & slope a_1 -> f(x)=a_0 + a_1 x
		// d=2: parabola
		// d>=2: continuous non-linear curve 
		// E.g. d=5: 4 extrema
		// d = 10 -> NumKoeff: 66 -> but only 64 lattice points are known
		int degree = 5; //Polynomial's degree: 2,...,10
		
		// Number of Coefficients
		// TODO:
		int numCoeff = 0;
		for (int i = 0; i <= degree + 1; i++) {
			numCoeff += i;
		}
		
		// Number of Correspondences
		// TODO:
		int numCorresp = nx * ny;
		
		// Print out of the used parameters
		System.out.println("Polynom of degree: " + degree);
		System.out.println("Number of Coefficients: " + numCoeff);
		System.out.println("Number of Correspondences: " + numCorresp);
		
		// 3.Create the matrix A
		SimpleMatrix A = new SimpleMatrix(numCorresp, numCoeff);
		A.zeros();
		
		// Realign the grid matrix into a vector
		// Easier access in the next step
		SimpleVector Xu2_vec = new SimpleVector(numCorresp);
		SimpleVector Yu2_vec = new SimpleVector(numCorresp);
		SimpleVector Xd2_vec = new SimpleVector(numCorresp);
		SimpleVector Yd2_vec = new SimpleVector(numCorresp);
		
		for(int i = 0; i < ny; i++)
		{
			for(int j = 0; j < nx; j++)
			{
				Xu2_vec.setElementValue(i * ny + j, Xu2.getElement(j, i));
				Yu2_vec.setElementValue(i * ny + j, Yu2.getElement(j, i));
				Xd2_vec.setElementValue(i * ny + j, Xd2.getElement(j, i));
				Yd2_vec.setElementValue(i * ny + j, Yd2.getElement(j, i));
			}
		}
		
		// Compute matrix A
		// loop over rows of A
		for(int r = 0; r < numCorresp; r++)
		{
			int cc = 0;
			// 2 loops for calculation of each row of A
			for(int i = 0; i <= degree; i++)
			{
				for(int j = 0; j <= (degree-i); j++)
				{
					// TODO:
					double curX = Xd2.getElement(r, r);
					double curY = Yd2.getElement(r, r);
					A.setElementValue(r, cc, (Math.pow(curY, j) * Math.pow(curX, i)));
					cc += 1;					
				}
			}
		}
		
		// Compute the pseudo-inverse of A with the help of the SVD (class: DecompositionSVD)
		DecompositionSVD svd = new DecompositionSVD(A);
		SimpleMatrix Ainv = A.inverse(InversionType.INVERT_SVD);
		
		// Compute the distortion coefficients
		// TODO
		SimpleVector u = SimpleOperators.multiply(Ainv, Xd2_vec);
		// TODO
		SimpleVector v = SimpleOperators.multiply(Ainv, Yd2_vec);
		
		// 4. Compute the distorted grid points (xDist, yDist) which are used to sample the
		// distorted image to get the undistorted image
		// (x,y) is the position in the undistorted image and (XDist,YDist) the
		// position in the distorted (observed) X-ray image. 
		
		Grid2D xDist = new Grid2D(imSize, imSize);  
		Grid2D yDist = new Grid2D(imSize, imSize); 
		
		for(int x = 0; x < imSize; x++)
		{
			for(int y = 0; y < imSize; y++)
			{
				int cc = 0;
				for(int k = 0; k <= degree; k++)
				{
					for(int l = 0; l <= degree - k; l++)
					{
						// TODO
						//xDist.setAtIndex(x, y, );
						// TODO
						// TODO
					}
				}
			}
		}
		
		Grid2D undistortedImage = new Grid2D(imSize, imSize);
		
		for(int i = 0; i < imSize; i++)
		{
			for(int j = 0; j < imSize; j++)
			{
				// TODO
				// TODO
			}
		}
		undistortedImage.show("Undistorted Image");
		
		Grid2D differenceImage = (Grid2D) NumericPointwiseOperators.subtractedBy(quadraticImage, undistortedImage);
		differenceImage.show("diffImage");
	}
	
	
	
	public static void main(String[] args) {
		ImageJ ij = new ImageJ();
		ImageUndistortion iu = new ImageUndistortion();
		iu.doImageUndstortion();
	}
}
