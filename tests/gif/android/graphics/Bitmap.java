package android.graphics;
/** JVM test-only pixel source; never packaged in the APK. */
public final class Bitmap {
    final int width,height;final int[] pixels;
    public Bitmap(int width,int height,int[] pixels){this.width=width;this.height=height;this.pixels=pixels;}
    public int getWidth(){return width;}public int getHeight(){return height;}
    public void getPixels(int[] dst,int offset,int stride,int x,int y,int width,int height){for(int row=0;row<height;row++)System.arraycopy(pixels,(y+row)*this.width+x,dst,offset+row*stride,width);}
}
