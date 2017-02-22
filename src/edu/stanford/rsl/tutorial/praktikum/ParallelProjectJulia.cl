typedef float TvoxelValue;
typedef float Tcoord_dev;

   __constant sampler_t sampler = CLK_NORMALIZED_COORDS_FALSE | CLK_ADDRESS_CLAMP_TO_EDGE | CLK_FILTER_LINEAR;


kernel void projection(
	int sizereconstruction,
	float spacing,
	float deltaTheta,
	float deltaS,
	float maxS,
	float maxTheta,
	__read_only image2d_t sinogram,
	__global TvoxelValue* result
	) {
    
    
	const unsigned int x = get_global_id(0); // theta
	const unsigned int y = get_global_id(1); // s
	
	if (x > sizereconstruction || y > sizereconstruction) {
		return;
	}
	
	unsigned int yStride = sizereconstruction;
	unsigned long idx = y*yStride + x;
	
	float worldX = x*spacing-((sizereconstruction -1) * (spacing/2));
	float worldY = y*spacing-((sizereconstruction -1) * (spacing/2));
	
	float s = maxS*deltaS;
	
	float sum = .0f;
	
	for (int  i = 0; i < maxTheta; i++) {
		// compute theta [rad] and angular functions.
		float theta = deltaTheta * i;
		float cosTheta = cos(((2*M_PI)/360)*theta);
	    float sinTheta = sin(((2*M_PI)/360)*theta);
	    
	    float2 p1 = {worldX, worldY};
	    float2 p2 = {cosTheta, sinTheta};
	    
	    float distance = dot(p1,p2); //projektionslaenge
	    
	    distance = distance + s/2;
	    distance = distance/deltaS;
	    
	    //float2 projcords= {distance*cosTheta +0.5f, distance*sinTheta +0.5f};
	    float2 projcords = {distance+0.5f, i+0.5f};
	    
	    float val = read_imagef(sinogram, sampler, projcords).x;
	    
	    sum = sum + val;
	}
	
	result[idx] = sum;
	
	return;
}