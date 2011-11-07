package process;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ImageProcessor;

import java.util.ArrayList;

import mpicbg.imglib.container.imageplus.ImagePlusContainer;
import mpicbg.imglib.container.imageplus.ImagePlusContainerFactory;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.exception.ImgLibException;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.interpolation.Interpolator;
import mpicbg.imglib.interpolation.linear.LinearInterpolatorFactory;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyValueFactory;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.AbstractAffineModel3D;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.InvertibleCoordinateTransform;
import mpicbg.models.NoninvertibleModelException;
import mpicbg.spim.registration.bead.BeadRegistration;

public class OverlayFusion 
{
	public static <T extends RealType<T>> CompositeImage createOverlay( final T targetType, final ImagePlus imp1, final ImagePlus imp2, final InvertibleBoundable finalModel1, final InvertibleBoundable finalModel2, final int dimensionality ) 
	{
		final ArrayList< ImagePlus > images = new ArrayList<ImagePlus>();
		images.add( imp1 );
		images.add( imp2 );
		
		final ArrayList< InvertibleBoundable > models = new ArrayList<InvertibleBoundable>();
		models.add( finalModel1 );
		models.add( finalModel2 );
		
		return createOverlay( targetType, images, models, dimensionality );
	}
	
	public static <T extends RealType<T>> CompositeImage createOverlay( final T targetType, final ArrayList<ImagePlus> images, final ArrayList<InvertibleBoundable> models, final int dimensionality )
	{	
		final int numImages = images.size();

		// the size of the new image
		final int[] size = new int[ dimensionality ];
		// the offset relative to the output image which starts with its local coordinates (0,0,0)
		final float[] offset = new float[ dimensionality ];

		// estimate the boundaries of the output image and the offset for fusion (negative coordinates after transform have to be shifted to 0,0,0)
		estimateBounds( offset, size, images, models, dimensionality );
		
		// for output
		final ImageFactory<T> f = new ImageFactory<T>( targetType, new ImagePlusContainerFactory() );
		// the composite
		final ImageStack stack = new ImageStack( size[ 0 ], size[ 1 ] );
		
		int numChannels = 0;
		
		//loop over all images
		for ( int i = 0; i < images.size(); ++i )
		{
			final ImagePlus imp = images.get( i );
			
			// loop over all channels
			for ( int c = 1; c <= imp.getNChannels(); ++c )
			{
				final Image<T> out = f.createImage( size );
				fuseChannel( out, ImageJFunctions.convertFloat( getImageChunk( imp, c, 1 ) ), offset, models.get( i ) );
				try 
				{
					final ImagePlus outImp = ((ImagePlusContainer<?,?>)out.getContainer()).getImagePlus();
					for ( int z = 1; z <= out.getDimension( 2 ); ++z )
						stack.addSlice( imp.getTitle(), outImp.getStack().getProcessor( z ) );
				} 
				catch (ImgLibException e) 
				{
					IJ.log( "Output image has no ImageJ type: " + e );
				}
				
				// count all channels
				++numChannels;
			}
		}

		//convertXYZCT ...
		ImagePlus result = new ImagePlus( "overlay " + images.get( 0 ).getTitle() + " ... " + images.get( numImages - 1 ).getTitle(), stack );
		
		// numchannels, z-slices, timepoints (but right now the order is still XYZCT)
		if ( dimensionality == 3 )
		{
			result.setDimensions( size[ 2 ], numChannels, 1 );
			result = OverlayFusion.switchZCinXYCZT( result );
		}
		else
		{
			result.setDimensions( numChannels, 1, 1 );
		}
		
		return new CompositeImage( result );
	}

	public static void estimateBounds( final float[] offset, final int[] size, final ArrayList<ImagePlus> images, final ArrayList<InvertibleBoundable> models, final int dimensionality )
	{
		final int numImages = images.size();
		
		// estimate the bounaries of the output image
		final float[][] max = new float[ numImages ][];
		final float[][] min = new float[ numImages ][ dimensionality ];
		
		if ( dimensionality == 2 )
		{
			for ( int i = 0; i < numImages; ++i )
				max[ i ] = new float[] { images.get( i ).getWidth(), images.get( i ).getHeight() };
		}
		else
		{
			for ( int i = 0; i < numImages; ++i )
			{
				max[ i ] = new float[] { images.get( i ).getWidth(), images.get( i ).getHeight(), images.get( i ).getNSlices() };
				BeadRegistration.concatenateAxialScaling( (AbstractAffineModel3D<?>)models.get( i ), images.get( i ).getCalibration().pixelDepth / images.get( i ).getCalibration().pixelWidth );
			}
		}

		// casts of the models
		final ArrayList<InvertibleBoundable> boundables = new ArrayList<InvertibleBoundable>();
		
		for ( int i = 0; i < models.size(); ++i )
		{
			final InvertibleBoundable boundable = (InvertibleBoundable)models.get( i ); 
			boundables.add( boundable );
			
			boundable.estimateBounds( min[ i ], max[ i ] );
		}
		//IJ.log( imp1.getTitle() + ": " + Util.printCoordinates( min1 ) + " -> " + Util.printCoordinates( max1 ) );
		//IJ.log( imp2.getTitle() + ": " + Util.printCoordinates( min2 ) + " -> " + Util.printCoordinates( max2 ) );
		
		// dimensions of the final image
		final float[] minImg = new float[ dimensionality ];
		final float[] maxImg = new float[ dimensionality ];

		for ( int d = 0; d < dimensionality; ++d )
		{
			// the image might be rotated so that min is actually max
			maxImg[ d ] = Math.max( Math.max( max[ 0 ][ d ], max[ 1 ][ d ] ), Math.max( min[ 0 ][ d ], min[ 1 ][ d ]) );
			minImg[ d ] = Math.min( Math.min( max[ 0 ][ d ], max[ 1 ][ d ] ), Math.min( min[ 0 ][ d ], min[ 1 ][ d ]) );
			
			for ( int i = 2; i < images.size(); ++i )
			{
				maxImg[ d ] = Math.max( maxImg[ d ], Math.max( min[ i ][ d ], max[ i ][ d ]) );
				minImg[ d ] = Math.min( minImg[ d ], Math.min( min[ i ][ d ], max[ i ][ d ]) );	
			}
		}
		
		//IJ.log( imp1.getTitle() + ": " + Util.printCoordinates( min1 ) + " -> " + Util.printCoordinates( max1 ) );
		//IJ.log( imp2.getTitle() + ": " + Util.printCoordinates( min2 ) + " -> " + Util.printCoordinates( max2 ) );
		//IJ.log( "output: " + Util.printCoordinates( minImg ) + " -> " + Util.printCoordinates( maxImg ) );

		// the size of the new image
		//final int[] size = new int[ dimensionality ];
		// the offset relative to the output image which starts with its local coordinates (0,0,0)
		//final float[] offset = new float[ dimensionality ];
		
		for ( int d = 0; d < dimensionality; ++d )
		{
			size[ d ] = Math.round( maxImg[ d ] - minImg[ d ] ) + 1;
			offset[ d ] = minImg[ d ];			
		}
		
		//IJ.log( "size: " + Util.printCoordinates( size ) );
		//IJ.log( "offset: " + Util.printCoordinates( offset ) );		
	}
	
	/**
	 * Fuse one slice/volume (one channel)
	 * 
	 * @param output - same the type of the ImagePlus input
	 * @param input - FloatType, because of Interpolation that needs to be done
	 * @param transform - the transformation
	 */
	protected static <T extends RealType<T>> void fuseChannel( final Image<T> output, final Image<FloatType> input, final float[] offset, final InvertibleCoordinateTransform transform )
	{
		final int dims = output.getNumDimensions();
		final LocalizableCursor<T> out = output.createLocalizableCursor();
		final Interpolator<FloatType> in = input.createInterpolator( new LinearInterpolatorFactory<FloatType>( new OutOfBoundsStrategyValueFactory<FloatType>() ) );
		
		final float[] tmp = new float[ input.getNumDimensions() ];
		
		try 
		{
			while ( out.hasNext() )
			{
				out.fwd();
				
				for ( int d = 0; d < dims; ++d )
					tmp[ d ] = out.getPosition( d ) + offset[ d ];
				
				transform.applyInverseInPlace( tmp );
	
				in.setPosition( tmp );			
				out.getType().setReal( in.getType().get() );
			}
		} 
		catch (NoninvertibleModelException e) 
		{
			IJ.log( "Cannot invert model, qutting." );
			return;
		}
	}

	
	/**
	 * Returns an {@link ImagePlus} for a 2d or 3d stack where ImageProcessors are not copied but just added.
	 * 
	 * @param imp - the input image
	 * @param channel - which channel (first channel is 1, NOT 0)
	 * @param timepoint - which timepoint (first timepoint is 1, NOT 0)
	 */
	public static ImagePlus getImageChunk( final ImagePlus imp, final int channel, final int timepoint )
	{
		if ( imp.getNSlices() == 1 )
		{
			return new ImagePlus( "", imp.getStack().getProcessor( imp.getStackIndex( channel, 1, timepoint ) ) );
		}
		else
		{
			final ImageStack stack = new ImageStack( imp.getWidth(), imp.getHeight() );
			
			for ( int z = 1; z < imp.getNSlices(); ++z )
			{
				final int index = imp.getStackIndex( channel, z, timepoint );
				final ImageProcessor ip = imp.getStack().getProcessor( index );
				stack.addSlice( imp.getStack().getSliceLabel( index ), ip );
			}
			
			return new ImagePlus( "", stack );
		}
			
	}
	
	/**
	 * Rearranges an ImageJ XYCZT Hyperstack into XYZCT without wasting memory for processing 3d images as a chunk,
	 * if it is already XYZCT it will shuffle it back to XYCZT
	 * 
	 * @param imp - the input {@link ImagePlus}
	 * @return - an {@link ImagePlus} which can be the same instance if the image is XYZT, XYZ, XYT or XY - otherwise a new instance
	 * containing the same processors but in the new order XYZCT
	 */
	public static ImagePlus switchZCinXYCZT( final ImagePlus imp )
	{
		final int numChannels = imp.getNChannels();
		final int numTimepoints = imp.getNFrames();
		final int numZStacks = imp.getNSlices();
		
		// there is only one channel
		if ( numChannels == 1 )
			return imp;
		
		// it is only XYC(T)
		if ( numZStacks == 1 )
			return imp;
		
		// now we have to rearrange
		final ImageStack stack = new ImageStack( imp.getWidth(), imp.getHeight() );
		
		for ( int t = 1; t <= numTimepoints; ++t )
		{
			for ( int c = 1; c <= numChannels; ++c )
			{
				for ( int z = 1; z <= numZStacks; ++z )
				{
					final int index = imp.getStackIndex( c, z, t );
					final ImageProcessor ip = imp.getStack().getProcessor( index );
					stack.addSlice( imp.getStack().getSliceLabel( index ), ip );
				}
			}
		}
		
		String newTitle;
		if ( imp.getTitle().startsWith( "[XYZCT]" ) )
			newTitle = imp.getTitle().substring( 8, imp.getTitle().length() );
		else
			newTitle = "[XYZCT] " + imp.getTitle();
		
		final ImagePlus result = new ImagePlus( newTitle, stack );
		// numchannels, z-slices, timepoints 
		// but of course now reversed...
		result.setDimensions( numZStacks, numChannels, numTimepoints );
		final CompositeImage composite = new CompositeImage( result );
		
		return composite;
	}

}
