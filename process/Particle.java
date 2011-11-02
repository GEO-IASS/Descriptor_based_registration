package process;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.util.Util;
import mpicbg.models.Point;
import fiji.util.node.Leaf;

public class Particle extends Point implements Leaf<Particle>
{
	private static final long serialVersionUID = 1L;

	final protected int id;
	
	protected DifferenceOfGaussianPeak<FloatType> peak;
	protected double weight = 1;
	protected float distance = -1;
	float diameter = 1;

	public Particle( final int id, final float[] location, final DifferenceOfGaussianPeak<FloatType> peak ) 
	{
		super( location );
		this.id = id;
		this.peak = peak;
	}

	public DifferenceOfGaussianPeak<FloatType> getPeak() { return peak; }
	
	/**
	 * Restores the local and global coordinates from the peak that feeded it initially,
	 * they might have been changed by applying a model during the optimization
	 */
	public void restoreCoordinates()
	{
		for ( int d = 0; d < l.length; ++d )
			l[ d ] = w[ d ] = peak.getSubPixelPosition( d );
	}
	public int getID() { return id; }	
	public void setWeight( final double weight ){ this.weight = weight; }
	public double getWeight(){ return weight; }
	public void setDiameter( final float diameter ) { this.diameter = diameter; }
	public float getDiameter() { return diameter; }

	final public void setW( final float[] wn )
	{
		for ( int i = 0; i < Math.min( w.length, wn.length ); ++i )
			w[i] = wn[i];
	}
	
	final public void resetW()
	{
		for ( int i = 0; i < w.length; ++i )
			w[i] = l[i];
	}

	protected boolean useW = true;
	
	public void setUseW( final boolean useW ) { this.useW = useW; } 
	public boolean getUseW() { return useW; } 
	
	public void setDistance( float distance ) { this.distance = distance;	}
	public float getDistance() { return this.distance;	}


	@Override
	public float get( final int k ) 
	{
		if ( useW )
			return w[ k ];
		else
			return l[ k ];
	}	
	
	public void set( final float v, final int k ) 
	{
		if ( useW )
			w[ k ] = v;
		else
			l[ k ] = v;
	}	

	@Override
	public String toString()
	{
		return "DustParticle " + getID() + " l"+ Util.printCoordinates( getL() ) + "; w"+ Util.printCoordinates( getW() );		
	}

	public boolean isLeaf() { return true; }

	@Override
	public float distanceTo( final Particle o )
	{
		final float x = o.get( 0 ) - get( 0 );
		final float y = o.get( 1 ) - get( 1 );
		
		return (float)Math.sqrt(x*x + y*y);
	}
	
	@Override
	public Particle[] createArray( final int n ){ return new Particle[ n ];	}

	@Override
	public int getNumDimensions(){ return 2; }
	
	public boolean equals( final Particle o )
	{
		if ( useW )
		{
			for ( int d = 0; d < 2; ++d )
				if ( w[ d ] != o.w[ d ] )
					return false;			
		}
		else
		{
			for ( int d = 0; d < 2; ++d )
				if ( l[ d ] != o.l[ d ] )
					return false;						
		}
				
		return true;
	}
	
	public static boolean equals( final Particle nucleus1, final Particle nucleus2 )
	{
		if ( nucleus1.getID() == nucleus2.getID() )
			return true;
		else
			return false;
	}	
}
