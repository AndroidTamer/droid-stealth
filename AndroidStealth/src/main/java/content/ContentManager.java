package content;

import static content.ConcealCrypto.CryptoMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.content.Context;
import android.util.Log;
import com.facebook.crypto.cipher.NativeGCMCipherException;

/**
 * ContentManager which copies the files to the local data directory Created by Alex on 13-3-14.
 */
public class ContentManager implements IContentManager {
	private ConcealCrypto crypto;
	private File mDataDir;
	private List<ContentChangedListener> mListeners = new ArrayList<ContentChangedListener>();

	public ContentManager(Context context) {
		mDataDir = context.getExternalFilesDir(null);
		crypto = new ConcealCrypto(context);
	}

	/**
	 * Helper function to copy a file internally
	 *
	 * @param sourceFile
	 * @param destFile
	 * @throws IOException
	 */
	private static void copyFile(File sourceFile, File destFile) throws IOException {
		if (!destFile.exists()) {
			destFile.createNewFile();
		}

		FileChannel source = null;
		FileChannel destination = null;

		try {
			source = new FileInputStream(sourceFile).getChannel();
			destination = new FileOutputStream(destFile).getChannel();
			destination.transferFrom(source, 0, source.size());

			Log.d("ContentManager.copyFile", "Copied the file");
		}
		finally {
			if (source != null) {
				source.close();
			}
			if (destination != null) {
				destination.close();
			}
		}
	}


	@Override
	public Collection<ContentItem> getStoredContent() {
		File[] files = mDataDir.listFiles();
		ArrayList<ContentItem> itemArrayList = new ArrayList<ContentItem>();

		for (File file : files) {
			itemArrayList.add(new ContentItem(file, file.getName()));
		}

		return itemArrayList;
	}

	@Override
	public boolean addItem(File item) {
		try {
			copyFile(item, new File(mDataDir, item.getName()));
			notifyListeners();
			return true;
		}
		catch (IOException e) {
			return false;
		}
	}

	@Override
	public boolean removeItem(ContentItem item) {
		boolean removed = item.getFile().delete();
		if (removed) {
			notifyListeners();
		}
		return removed;
	}

	/**
	 * Encrypts all files in the {@param contentItemCollection}. Deletes the original file after encrypting them.
	 *
	 * @return true if ALL files are encrypted successfully, false otherwise.
	 */
	@Override
	public boolean encryptItems(Collection<ContentItem> contentItemCollection, EncryptionService service) {
		boolean success = true;

		for (ContentItem contentItem : contentItemCollection) {
			success = encryptItem(contentItem, service) && success;
		}

		if (success) {
			Log.i(this.getClass().toString(), "Encrypted items:");
		}
		else {
			Log.e(this.getClass().toString(), "Encrypted with errors:");
		}
		for (ContentItem contentItem : contentItemCollection) {
			Log.e(this.getClass().toString(), contentItem.getFile().getAbsolutePath());
		}

		notifyListeners();

		return success;
	}

	private boolean encryptItem(ContentItem contentItem, EncryptionService service) {

		try {
			Log.d(this.getClass().toString() + ".encryptItem",
					"Encrypting file " + contentItem.getFile().getAbsolutePath());
			File encryptedFile = new File(mDataDir + "/" + contentItem.getFileName() + ".CRYPT");
			encryptedFile.createNewFile();

			Future taskFuture = service.addCryptoTask(encryptedFile, contentItem.getFile(), encryptedFile.getName(),
					CryptoMode.ENCRYPT);

			taskFuture.get(1, TimeUnit.MINUTES);

			notifyListeners();

			return true;
		}
		catch (IOException e) {
			Log.e(this.getClass().toString() + ".encryptItem", "Error in encrypting data", e);
		}
		catch (InterruptedException e) {
			Log.e(this.getClass().toString() + ".encryptItem", "Interrupted while encrypting", e);
		}
		catch (ExecutionException e) {
			Log.e(this.getClass().toString() + ".encryptItem", "Exception while executing encryption", e);
		}
		catch (TimeoutException e) {
			Log.e(this.getClass().toString() + ".encryptItem", "Timed out while waiting for encryption", e);
		}

		return false;
	}

	/**
	 * Decrypts all files in the {@param contentItemCollection}. Deletes the encrypted files after decrypting them.
	 *
	 * @return true if ALL files are decrypted successfully, false otherwise.
	 */
	@Override
	public boolean decryptItems(Collection<ContentItem> contentItemCollection, EncryptionService service) {
		boolean success = true;

		for (ContentItem contentItem : contentItemCollection) {
			success = decryptItem(contentItem, service) && success;
		}

		if (success) {
			Log.i(this.getClass().toString() + ".decryptItems", "Decrypted items:");
		}
		else {
			Log.w(this.getClass().toString() + ".decryptItems", "Decrypted with errors:");
		}
		for (ContentItem contentItem : contentItemCollection) {
			System.out.println("\t" + contentItem.getFileName());
		}

		notifyListeners();

		return success;
	}

	public boolean decryptItem(ContentItem contentItem, EncryptionService service) {
		try {
			// Remove .CRYPT from filename
			String filename = mDataDir + "/" + contentItem.getFileName();
			filename = filename.substring(0, filename.length() - 6);

			// Create target file
			File decryptedFile = new File(filename);
			decryptedFile.createNewFile();

			Future taskFuture = service.addCryptoTask(contentItem.getFile(), decryptedFile, decryptedFile.getName(),
					CryptoMode.DECRYPT);

			taskFuture.get(1, TimeUnit.MINUTES);

			notifyListeners();
		}
		catch (IOException e) {
			if (e instanceof NativeGCMCipherException) {
				Log.e(this.getClass().toString() + ".decryptItem", "Error in decrypting data", e);
				contentItem.getFile().delete();
			}
			else {
				Log.e(this.getClass().toString() + ".decryptItem", "Error in decrypting data", e);
			}
		}
		catch (InterruptedException e) {
			Log.e(this.getClass().toString()+".decryptItem", "Interrupted while decrypting", e);
		}
		catch (ExecutionException e) {
			Log.e(this.getClass().toString() + ".decryptItem", "Exception while executing decryption", e);
		}
		catch (TimeoutException e) {
			Log.e(this.getClass().toString() + ".decryptItem", "Timed out while waiting for decryption", e);
		}

		return false;
	}

	@Override
	public boolean removeItems(Collection<ContentItem> itemCollection) {
		boolean noFailure = true;
		boolean singleSuccess = false;
		for (ContentItem item : itemCollection) {
			boolean removed = item.getFile().delete();
			if (removed) {
				singleSuccess = true;
			}
			else {
				noFailure = false;
			}
		}

		//Empty list, we 'failed' anyway
		if (itemCollection.size() == 0) {
			noFailure = false;
		}

		if (singleSuccess) {
			notifyListeners();
		}

		return noFailure;
	}

	@Override
	public void addContentChangedListener(ContentChangedListener listener) {
		if (!mListeners.contains(listener)) {
			mListeners.add(listener);
		}
	}

	@Override
	public boolean removeContentChangedListener(ContentChangedListener listener) {
		return mListeners.remove(listener);
	}

	@Override
	public void removeAllContent() {
		for (File file : mDataDir.listFiles()) {
			file.delete();
		}
	}

	/**
	 * Notifies all listeners of a change in content
	 */
	public void notifyListeners() {
		for (ContentChangedListener listener : mListeners) {
			listener.contentChanged();
		}
	}
}
