package it.feio.android.omninotes.async;

import it.feio.android.omninotes.OmniNotes;
import it.feio.android.omninotes.R;
import it.feio.android.omninotes.models.Attachment;
import it.feio.android.omninotes.models.ThumbnailLruCache;
import it.feio.android.omninotes.utils.BitmapDecoder;
import it.feio.android.omninotes.utils.Constants;
import it.feio.android.omninotes.utils.StorageManager;

import java.io.FileNotFoundException;
import java.lang.ref.WeakReference;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
import android.provider.MediaStore.Images.Thumbnails;
import android.util.Log;
import android.widget.ImageView;

public class ListThumbnailLoaderTask extends
		AsyncTask<Attachment, Void, Bitmap> {

	private final Activity mActivity;
	private final WeakReference<ImageView> imageViewReference;
	private int width = 100;
	private int height = 100;

	public ListThumbnailLoaderTask(Activity activity, ImageView imageView,
			int thumbnailSize) {
		this.mActivity = activity;
		imageViewReference = new WeakReference<ImageView>(imageView);
		width = thumbnailSize;
		height = thumbnailSize;
	}

	@Override
	protected Bitmap doInBackground(Attachment... params) {
		Bitmap bmp = null;
		Attachment mAttachment = params[0];

		String path = mAttachment.getUri().getPath();		
		// Creating a key based on path and thumbnail size to re-use the same
		// AsyncTask both for list and detail
		String cacheKey = path + width;

		// Requesting from Application an instance of the cache
		ThumbnailLruCache cache = ((OmniNotes) mActivity.getApplication()).getThumbnailLruCache();

		// Video
		if (Constants.MIME_TYPE_VIDEO.equals(mAttachment.getMime_type())) {
			// Tries to retrieve full path from ContentResolver if is a new
			// video
			path = StorageManager.getRealPathFromURI(mActivity,
					mAttachment.getUri());
			// .. or directly from local directory otherwise
			if (path == null) {
				path = mAttachment.getUri().getPath();
			}

			// Fetch from cache if possible
			bmp = cache.getBitmap(cacheKey);
			// Otherwise creates thumbnail
			if (bmp == null) {
				bmp = ThumbnailUtils.createVideoThumbnail(path,
						Thumbnails.MINI_KIND);
				bmp = createVideoThumbnail(bmp);
				cache.addBitmap(cacheKey, bmp);
			}

			// Image
		} else if (Constants.MIME_TYPE_IMAGE.equals(mAttachment.getMime_type())) {
			try {
				// Fetch from cache if possible
				bmp = cache.getBitmap(cacheKey);
				// Otherwise creates thumbnail
				if (bmp == null) {
					try {
						bmp = checkIfBroken(BitmapDecoder.decodeSampledFromUri(
								mActivity, mAttachment.getUri(), width, height));
						cache.addBitmap(path, bmp);
					} catch (FileNotFoundException e) {
						Log.e(Constants.TAG,
								"Error getting bitmap for thumbnail " + path);
					}
				}
			} catch (NullPointerException e) {
				bmp = checkIfBroken(null);
				cache.addBitmap(cacheKey, bmp);
			}

			// Audio
		} else if (Constants.MIME_TYPE_AUDIO.equals(mAttachment.getMime_type())) {
			// Fetch from cache if possible
			bmp = cache.getBitmap(cacheKey);
			// Otherwise creates thumbnail
			if (bmp == null) {
				bmp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeResource(
						mActivity.getResources(), R.drawable.play), width, height);
				bmp = BitmapDecoder.drawTextToBitmap(mActivity, bmp, mAttachment
						.getUri().getLastPathSegment(), null, -10, 10, mActivity
						.getResources().getColor(R.color.text_gray));
				cache.addBitmap(cacheKey, bmp);
			}
		}

		return bmp;
	}

	@Override
	protected void onPostExecute(Bitmap bitmap) {
		if (imageViewReference != null && bitmap != null) {
			final ImageView imageView = imageViewReference.get();
			if (imageView != null) {
				imageView.setImageBitmap(bitmap);
			}
		}
	}

	/**
	 * Draws a watermark on ImageView to highlight videos
	 * 
	 * @param bmp
	 * @param overlay
	 * @return
	 */
	public Bitmap createVideoThumbnail(Bitmap video) {
		Bitmap mark = ThumbnailUtils.extractThumbnail(
				BitmapFactory.decodeResource(mActivity.getResources(),
						R.drawable.play_white), width, height);
		Bitmap thumbnail = Bitmap.createBitmap(width, height,
				Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(thumbnail);
		Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);

		canvas.drawBitmap(checkIfBroken(video), 0, 0, null);
		canvas.drawBitmap(mark, 0, 0, null);

		return thumbnail;
	}

	private Bitmap checkIfBroken(Bitmap bmp) {

		// In case no thumbnail can be extracted from video
		if (bmp == null) {
			bmp = ThumbnailUtils.extractThumbnail(BitmapFactory.decodeResource(
					mActivity.getResources(), R.drawable.attachment_broken),
					width, height);
		}
		return bmp;
	}

}