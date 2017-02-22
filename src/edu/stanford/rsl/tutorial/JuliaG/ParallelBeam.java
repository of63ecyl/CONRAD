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
  
public class ParallelBeam {
      
    public static Grid2D sinogram (Grid2D grid, double maxTheta, double deltaTheta, double maxS, double deltaS) {
                  
        int maxSsteps = (int)((maxS/deltaS)); //Anzahl der Rays
        int maxThetasteps = (int)((maxTheta/deltaTheta)); //Anzahl der Aufnahmen (aus wie vielen verschiedenen Winkel)
           
        grid.setOrigin(-(grid.getSize()[0]*grid.getSpacing()[0])/2, -(grid.getSize()[1]*grid.getSpacing()[1])/2);
          
        // Sinogram erstellen, soll selbe Groesse haben wie Bild und auch dasselbe spacing
        // buffer (Area), width, height //warum buffer?
        Grid2D sino = new Grid2D(maxSsteps, maxThetasteps);
        sino.setSpacing(deltaS, deltaTheta);
           
        // Box erstellen mit derselben Groesse wie Bild
        Box box = new Box(grid.getSize()[0]*grid.getSpacing()[0], grid.getSize()[1]*grid.getSpacing()[1], 2);
          
        Translation trans = new Translation(
                -(grid.getSize()[0] * grid.getSpacing()[0])/2, -(grid.getSize()[1] * grid.getSpacing()[1])/2, -1
                );
        Transform inverse = trans.inverse();
   
        // Erstelle Translationsvektor Image to world, Box braucht man in Image coordinates -> inverse
        box.applyTransform(trans);
           
        // Erst ueber alle Winkel laufen, d.h. man benoetigt die Anzahl der Schritte
        for(int i = 0; i<maxThetasteps; i++) {
            // Tatsaechlichen Winkel berechnen
            double theta = deltaTheta*i;
           
            double cosTheta = Math.cos(((2*Math.PI)/360)*theta);
            double sinTheta = Math.sin(((2*Math.PI)/360)*theta);
               
            //fuer jeden Winkel werden alle Werte berechnet, fuer jeden Punkt auf S
            for (int j = 0; j<maxSsteps; j++){
                // Laenge s berechnen, d.h. Punkt durch Koordinatenachse s an dem wir uns befinden -> jetzt benoetigt man alle Punkte die auf der Geraden senkrecht zu/durch diesen Punkt verlaufen
                double s = j*deltaS - maxS/2;
                   
                //Punkt an dem sich Koordinatenachse s und Senkrechte schneiden
                // Man benoetigt danach Linie, und diese wird nur mit PointND gesetzt
                PointND p1 = new PointND(s*cosTheta, s*sinTheta, .0d);
                // p2: Setzt die Richtung des Punktes p2, senkrecht zu s
                PointND p2 = new PointND((cosTheta*s - sinTheta), (sinTheta*s+cosTheta), .0d);
                   
                //Linie durch beide Punkte
                StraightLine line = new StraightLine(p1, p2);
                   
                // Linie muss Box schneiden, um Anfangs- und Endpunkt zu erhalten, und es sollen alle Punkte der Geraden in einer ArrayList gespeichert werden
                ArrayList<PointND> list = box.intersect(line);
                double sum = 0;
                if (list.size() == 0) {
                    sum = 0;
                } else {
                    PointND startpoint = list.get(0);
                    PointND endpoint = list.get(list.size()-1);
                // Distanz von Anfangs und Endpunkt wird benoetigt, um ueber eine Schleife zu laufen und alle Werte der Punkte in Arraylist aufzuaddieren -> Wert in Sinogram
                // Abstractvector benoetigt um mit ihnen rechnen zu koennen
                   
                SimpleVector start = new SimpleVector(startpoint.getAbstractVector());
                SimpleVector end = new SimpleVector(endpoint.getAbstractVector());
                // Benoetige Steigung, bzw. Vektor der in die Richtung der Geraden zeigt, um vom Startpunkt aus in die richtige Richtung zu laufen und somit Punkt zu finden
                // Benoetige auch richtige Schrittweite
                end.subtract(start);
                   
                // koennen auch negativ sein -> L2 Norm, damit wir positive Distanz erhalten
                double distance = end.normL2();
                // Schrittweite berechnen, mit der auf Linie gelaufen wird (Schritt zum naechsten Pixel)
                end.divideBy(distance);
                   
                // initialisiere Wert der aufsummierten Punktewerte auf Linie
                  
                // Integral berechnen, uber senkrechte laufen und werte vom phantom für feste s und theta aufsummieren
                for (int k = 0; k < distance; k++) {
                    // Anfangspunkt
                    PointND begin = new PointND(startpoint);
                    // Laufe zu meinem Punkt, in oben berechneter Richtung, abhaengig von k
                    begin.getAbstractVector().add(end.multipliedBy(k));
                       
                    // Brauche nun Value an diesem Punkt, hierfuer muss Wert an x- und y-Koordinate berechnet werden (spacing beachten)
                    //double x = begin.get(0)/grid.getSpacing()[0]+grid.getOrigin()[0];
                    //double y = begin.get(1)/grid.getSpacing()[1]+grid.getOrigin()[1];
                    double x =  (begin.get(0)-grid.getOrigin()[0])/grid.getSpacing()[0];
                    double y =  (begin.get(1)-grid.getOrigin()[1])/grid.getSpacing()[1]; 
                      
                    // Interpolation, da Ort nicht immer direkt auf einen Pixel faellt, d.h. nicht auf einen genauen Wert
                       
                    double value = InterpolationOperators.interpolateLinear(grid, x, y);
                    // aufsummieren aller Werte ueber ganze Linie
                    sum = sum + value;
                       
                }
                }
                sino.setAtIndex(j, i, (float)sum);
              
//            Grid1D currentLine = sino.getSubGrid(0);
//            currentLine.show();
//            System.out.println();
            }
        }
        return sino;
          
    }
      
    public static Grid2D ramlak (Grid2D sino) {
          
        Grid2D filtered = new Grid2D(sino.getWidth(), sino.getHeight());
          
        Grid1DComplex filter = new Grid1DComplex(sino.getSize()[0]);
         
        double odd = -(1/(Math.PI*Math.PI));
 
        for (int i = 0; i<filter.getSize()[0]/2; i++) {
             
            if(i==(0)) {
                filter.setRealAtIndex(i, 0.25f);
            } else if((i%2)!=0){
                filter.setRealAtIndex(i, (float)odd*(1f/(i*i)));
                filter.setRealAtIndex(filter.getSize()[0]-i, (float)odd*(1f/(i*i)));
            }
        }
 
        filter.transformForward();        
         
        for (int y=0; y < sino.getHeight(); y++) {
            Grid1DComplex rowcomplex = new Grid1DComplex(sino.getSubGrid(y));
            rowcomplex.transformForward();
             
            for(int i = 0; i<rowcomplex.getSize()[0]; i++){
                rowcomplex.multiplyAtIndex(i, filter.getRealAtIndex(i), filter.getImagAtIndex(i));
            }
            rowcomplex.transformInverse();  
             
            for(int j = 0; j<filtered.getWidth(); j++){
                filtered.setAtIndex(j, y, rowcomplex.getRealAtIndex(j));
            }
        }
        return filtered;
    }
      
    public static Grid2D rampfilter (Grid2D sino){
        double decspacing = sino.getSpacing()[0];//detektorspacing
        Grid2D filtered = new Grid2D(sino.getWidth(), sino.getHeight());
          
        Grid1D filter = new Grid1D(FFTUtil.getNextPowerOfTwo(sino.getSize()[0]));//mal nach länge schaun wegen zero padding
        double deltaf = 1/(decspacing*filter.getNumberOfElements());
          
        double omega = 2*Math.PI*deltaf;
          
        // laufen von -laenge/2 bis 0
         
        for (int i = 0; i<filter.getNumberOfElements()/2; i++) {
            filter.setAtIndex(i, (float)Math.abs(omega*(i)));
            filter.setAtIndex(filter.getNumberOfElements()-1-i, (float)Math.abs(omega*i));
        }
        filter.show();
        Grid1DComplex filtercomplex = new Grid1DComplex(filter);
          
        for (int y = 0; y < sino.getHeight(); y++){
            Grid1DComplex rowcomplex = new Grid1DComplex(sino.getSubGrid(y));
            rowcomplex.transformForward();
              
            for(int i = 0; i<filter.getNumberOfElements(); i++){
                rowcomplex.multiplyAtIndex(i, filtercomplex.getRealAtIndex(i));
            }
            rowcomplex.transformInverse();
              
            for(int j = 0; j<filtered.getWidth(); j++){
                filtered.setAtIndex(j, y, rowcomplex.getRealAtIndex(j));
            }
              
         }
          
           
        return filtered;
    }
       
      
    public static Grid2D backprojection (int size, double spacing, Grid2D sinogram, double deltaTheta, double deltaS) {
          
        //aus gegebener Zieldimensionen leeres Grid erstellen, spacing setzen, world coordinates
        Grid2D backprojection = new Grid2D(size, size);
        backprojection.setSpacing(spacing, spacing);
        backprojection.setOrigin(-(backprojection.getSize()[0]*backprojection.getSpacing()[0])/2, -(backprojection.getSize()[1]*backprojection.getSpacing()[1])/2);
        //backprojection.setOrigin(-backprojection.getWidth()/2, -backprojection.getHeight()/2);
          
        double maxS = sinogram.getSize()[0];
        //double deltaTheta = sinogram.getSpacing()[1];
        double maxTheta = sinogram.getSize()[1];
        //double deltaS = sinogram.getSpacing()[0];
        double s = (maxS) * deltaS;
          
        //int maxSsteps = (int)((maxS/deltaS)); //Anzahl der Rays
        int maxThetasteps = (int)((maxTheta/deltaTheta));    //Anzahl der Aufnahmen (aus wie vielen verschiedenen Winkel)
          
              
        for (int x = 0; x < backprojection.getSize()[0]; x++){
            for (int y = 0; y < backprojection.getSize()[1]; y++){
                  
                float sum = .0f;
                  
                for(int i = 0; i<maxTheta; i++) {
                //for(int i = 0; i<maxThetasteps; i++) {
                    double theta = deltaTheta*i;
                    double cosTheta = Math.cos(((2*Math.PI)/360)*theta);
                    double sinTheta = Math.sin(((2*Math.PI)/360)*theta);
                      
                    PointND current = new PointND(backprojection.indexToPhysical(x, y));
                    SimpleVector currentvec = new SimpleVector(current.getAbstractVector());
                  
                    //PointND p1 = new PointND(backprojection.getOrigin());
                    PointND p2 = new PointND(cosTheta, sinTheta);
                  
                    //SimpleVector start = p1.getAbstractVector();
                    SimpleVector end = p2.getAbstractVector();
                  
                    double distance = SimpleOperators.multiplyInnerProd(currentvec, end);
                    //dektorlänge in mm wieder zurückrechnen in index damit man wert im sinogram nachschaun kann
                      
                    distance += s/2;
                    distance /= deltaS;
                    //double distance = distance1/deltaS + maxS/2;
//                    distance = distance/deltaS;
                    //System.out.println(distance);
                      
                    //subgrid: werte aus sinogram für bestimmen winkel theta, hier in abhängigkeit von i
                    Grid1D currentsino = sinogram.getSubGrid(i);
                      
                    // Überprüfen, ob distance zulässige werte hat
                    if (currentsino.getSize()[0] <= distance + 1
                            ||  distance < 0)
                        continue;
                      
                    float val = InterpolationOperators.interpolateLinear(currentsino, distance);
                    sum += val;
                }
                backprojection.setAtIndex(x, y, sum);
            }           
        }
        //NumericPointwiseOperators.divideBy(backprojection, (float) (maxThetasteps / Math.PI));
        return backprojection;
    }
  
    public static void main(String[] args) {
        new ImageJ();
        //BIO object = new BIO(256,256);
        SheppLogan object = new SheppLogan(256);
        object.show();
        //object.setSpacing(1);
        Grid2D sino = sinogram(object,180,1,300,1);
        sino.show("normal sino");
        Grid2D rampsino = rampfilter(sino);
        rampsino.show("rampsino");
        Grid2D ramlaksino = ramlak(sino);
        ramlaksino.show("ramlaksino");
        Grid2D result1 = backprojection(256,1,ramlaksino,1,1 );
        result1.show("backprojection ramlaksino");
        Grid2D result = backprojection(256,1,rampsino,1,1);
        result.show("backprojection rampsino");
    }
}