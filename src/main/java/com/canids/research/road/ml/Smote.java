package com.canids.research.road.ml;
import java.util.*;
/** SMOTE stage from paper Section IV-E.
 *
 * <p>Flow: select a minority row -> find its five nearest minority neighbors ->
 * choose one neighbor -> linearly interpolate a synthetic feature vector. The
 * runner applies it only to the training fold.
 */
public final class Smote {
    private Smote() {
    }
    public record Dataset(double[][] features, int[] labels) {
    }
    /** Adds synthetic rows until the requested minority/majority ratio is reached. */
    public static Dataset oversample(double[][] x, int[] y, double ratio, long seed) {
        if(x.length!=y.length)throw new IllegalArgumentException("Row counts differ");
        if(ratio<=0||ratio>1)throw new IllegalArgumentException("SMOTE ratio must be in (0,1]");
        int zero=0, one=0;
        for(int v:y) {
            if(v==0)zero++;
            else if(v==1)one++;
            else throw new IllegalArgumentException("Labels must be 0/1");
        }
        if(zero==0||one==0)return new Dataset(copy(x), y.clone());
        int label=zero<one?0:1;
        List<Integer> minority=new ArrayList<>();
        for(int i=0; i<y.length; i++)if(y[i]==label)minority.add(i);
        int need=Math.max(0, (int)Math.ceil(Math.max(zero, one)*ratio)-minority.size());
        if(need==0||minority.size()<2)return new Dataset(copy(x), y.clone());
        Random r=new Random(seed);
        List<double[]> rows=new ArrayList<>();
        List<Integer> labs=new ArrayList<>();
        for(int i=0; i<x.length; i++) {
            rows.add(x[i].clone());
            labs.add(y[i]);
        }
        for(int k=0; k<need; k++) {
            int base=minority.get(r.nextInt(minority.size()));
            List<Integer> near=new ArrayList<>(minority);
            near.remove(Integer.valueOf(base));
            near.sort(Comparator.comparingDouble(i->distance(x[base], x[i])));
            double[] a=x[base], b=x[near.get(r.nextInt(Math.min(5, near.size())))], s=new double[a.length];
            double alpha=r.nextDouble();
            for(int j=0; j<s.length; j++)s[j]=a[j]+alpha*(b[j]-a[j]);
            rows.add(s);
            labs.add(label);
        }
        return new Dataset(rows.toArray(double[][]::new), labs.stream().mapToInt(Integer::intValue).toArray());
    }
    private static double distance(double[]a, double[]b) {
        double s=0;
        for(int i=0; i<a.length; i++) {
            double d=a[i]-b[i];
            s+=d*d;
        }
        return s;
    }
    private static double[][] copy(double[][]a) {
        double[][]b=new double[a.length][];
        for(int i=0; i<a.length; i++)b[i]=a[i].clone();
        return b;
    }
}
