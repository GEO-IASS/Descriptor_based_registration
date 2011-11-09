package plugin;

import fiji.plugin.Bead_Registration;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.MultiLineLabel;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;

public class Hyperstack_rearranger implements PlugIn
{
	final private String myURL = "http://fly.mpi-cbg.de/preibisch";

	public static int defaultIndexChannels = 0;
	public static int defaultIndexSlices = 1;
	public static int defaultIndexFrames = 2;
	public String[] choice = new String[] { "Channels (c)", "Slices (z)", "Frames (t)" };
	
	@Override
	public void run(String arg0) 
	{
		final ImagePlus imp = WindowManager.getCurrentImage();
		
		final int nChannels = imp.getNChannels();
		final int nSlices = imp.getNSlices();
		final int nFrames = imp.getNFrames();
		
		if ( nChannels + nFrames + nSlices == 1 )
		{
			IJ.log( "This is only a 2d-image." );
			return;
		}
		
		final GenericDialog gd = new GenericDialog( "Re-order Hyperstack [" + imp.getTitle() + "]" );			
		
		gd.addChoice( "Channels (c) -> ", choice, choice[ defaultIndexChannels ] );
		gd.addChoice( "Slices (z) -> ", choice, choice[ defaultIndexSlices ] );
		gd.addChoice( "Frames (t) -> ", choice, choice[ defaultIndexFrames ] );
		gd.addMessage("");
		gd.addMessage("Current number of channels: " + nChannels );
		gd.addMessage("Current number of slices: " + nSlices );
		gd.addMessage("Current number of frames: " + nFrames );
		gd.addMessage("");
		gd.addMessage("This Plugin is developed by Stephan Preibisch\n" + myURL);

		MultiLineLabel text = (MultiLineLabel) gd.getMessage();
		Bead_Registration.addHyperLinkListener(text, myURL);
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		final int indexChannels = gd.getNextChoiceIndex();
		final int indexSlices = gd.getNextChoiceIndex();
		final int indexFrames = gd.getNextChoiceIndex();
		
		// is it valid?
		final int[] verify = new int[ 3 ];
		verify[ indexChannels ]++;
		verify[ indexSlices ]++;
		verify[ indexFrames ]++;
		
		if ( verify[ 0 ] != 1 || verify[ 1 ] != 1 || verify[ 2 ] != 1 )
		{
			IJ.log( "Mapping is inconsistent: each - channel, slices and frames have to be assigned to an input dimension." );
			return;
		}
		
		defaultIndexChannels = indexChannels;
		defaultIndexSlices = indexSlices;
		defaultIndexFrames = indexFrames;
		
		reorderHyperstack( imp, indexChannels, indexSlices, indexFrames, true, true );
	}

	/**
	 * Creates a new Hyperstack with a different order of dimensions, the ImageProcessors are not copied
	 * but just linked from the input ImagePlus
	 * 
	 * @param imp - the input ImagePlus
	 * @param newOrder - the new order, can be "CZT", "CTZ", "ZCT", "ZTC", TCZ" or "TZC"
	 * @param closeOldImp - close the old one?
	 * @param showNewImp - show the new one?
	 * 
	 * @return - a new ImagePlus with different order of Processors linked from the old ImagePlus
	 */
	public static ImagePlus reorderHyperstack( final ImagePlus imp, final String newOrder, final boolean closeOldImp, final boolean showNewImp )
	{
		if ( newOrder.equalsIgnoreCase( "CZT" ) )
			return reorderHyperstack( imp, 0, 1, 2, closeOldImp, showNewImp );
		else if ( newOrder.equalsIgnoreCase( "CTZ" ) )
			return reorderHyperstack( imp, 0, 2, 1, closeOldImp, showNewImp );
		else if ( newOrder.equalsIgnoreCase( "ZCT" ) )
			return reorderHyperstack( imp, 1, 0, 2, closeOldImp, showNewImp );
		else if ( newOrder.equalsIgnoreCase( "ZTC" ) )
			return reorderHyperstack( imp, 1, 2, 0, closeOldImp, showNewImp );
		else if ( newOrder.equalsIgnoreCase( "TCZ" ) )
			return reorderHyperstack( imp, 2, 0, 1, closeOldImp, showNewImp );
		else if ( newOrder.equalsIgnoreCase( "TZC" ) )
			return reorderHyperstack( imp, 2, 1, 0, closeOldImp, showNewImp );
		else
		{
			IJ.log( "Unknown reordering: " + newOrder );
			return null;
		}
	}
	
	/**
	 * Creates a new Hyperstack with a different order of dimensions, the ImageProcessors are not copied
	 * but just linked from the input ImagePlus
	 * 
	 * id's used here, reflecting (XY)CZT:
	 * channel = 0
	 * slices = 1
	 * frames = 2
	 * 
	 * @param imp - the input ImagePlus
	 * @param targetChannels - the new id for channel (0, 1 or 2, i.e. channels stays channels or become slices or frames)
	 * @param targetSlices - the new id for slices (0, 1 or 2 ...)
	 * @param targetFrames - the new id for frames (0, 1 or 2 ...)
	 * @param closeOldImp - close the old one?
	 * @param showNewImp - show the new one?
	 * 
	 * @return - a new ImagePlus with different order of Processors linked from the old ImagePlus
	 */
	public static CompositeImage reorderHyperstack( final ImagePlus imp, final int targetChannels, final int targetSlices, final int targetFrames, final boolean closeOldImp, final boolean showNewImp )
	{
		// dimensions of the input imageplus in order CZT
		final int[] dimensions = new int[] { imp.getNChannels(), imp.getNSlices(), imp.getNFrames() };

		// the new dimension assignments; 0->(c,z or t) 1->(c,z or t) 2->(c,z or t)
		final int[] newAssignment = new int[] { targetChannels, targetSlices, targetFrames };
		
		// we need a new stack
		final ImageStack stack = new ImageStack( imp.getWidth(), imp.getHeight() );

		// XYCZT is the order that ImageJ wants
		// so we arrange it like that.
		// However, we adjust the numbers to the new dimensions
		final int nChannelsNew = dimensions[ newAssignment[ 0 ] ];
		final int nSlicesNew = dimensions[ newAssignment[ 1 ] ];
		final int nFramesNew = dimensions[ newAssignment[ 2 ] ];
		
		// used to translate old -> new
		final int[] indexTmp = new int[ 3 ];
		
		for ( int t = 1; t <= nFramesNew; ++t )
		{
			for ( int z = 1; z <= nSlicesNew; ++z )
			{
				for ( int c = 1; c <= nChannelsNew; ++c )
				{
					indexTmp[ newAssignment[ 0 ] ] = c; 
					indexTmp[ newAssignment[ 1 ] ] = z; 
					indexTmp[ newAssignment[ 2 ] ] = t; 
					
					//final int index = imp.getStackIndex( c, z, t );
					final int index = imp.getStackIndex( indexTmp[ 0 ], indexTmp[ 1 ], indexTmp[ 2 ] );
					final ImageProcessor ip = imp.getStack().getProcessor( index );
					stack.addSlice( imp.getStack().getSliceLabel( index ), ip );
				}
			}
		}
		
		final ImagePlus newImp = new ImagePlus( imp.getTitle(), stack );
		newImp.setDimensions( nChannelsNew, nSlicesNew, nFramesNew );
		newImp.setCalibration( imp.getCalibration() );
		
		final CompositeImage c = new CompositeImage( newImp, CompositeImage.COMPOSITE );
		
		if ( closeOldImp )
			imp.close();
		
		if ( showNewImp )
			c.show(); 
		
		return c;
	}
	
}
