package com.acertainbookstore.business;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;
import com.acertainbookstore.utils.BookStoreUtility;


/** {@link TwoLevelLockingConcurrentCertainBookStore} implements the {@link BookStore} and
 * {@link StockManager} functionalities.
 *
 * @see BookStore
 * @see StockManager
 */
public class TwoLevelLockingConcurrentCertainBookStore implements BookStore, StockManager {

	private ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
	private Lock globalExclusiveLock = readWriteLock.writeLock();
	private Lock globalSharedLock = readWriteLock.readLock();

	/** The mapping of books from ISBN to {@link BookStoreBook}. */
	private Map<Integer, BookStoreBook> bookMap = null;
	//private ReentrantReadWriteLock databaseLock = null;
	private ConcurrentHashMap<Integer, ReentrantReadWriteLock> lockMap = null;
	/**
	 * Instantiates a new {@link CertainBookStore}.
	 */
	public TwoLevelLockingConcurrentCertainBookStore() {
		// Constructors are not synchronized
		bookMap = new HashMap<>();
		lockMap = new ConcurrentHashMap<>();
	}

	private void validateISBN(int ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) {
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}

		if (!bookMap.containsKey(ISBN)) {
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}



	private void lockLocal(int isbn, boolean isWriteLock) throws BookStoreException {
		ReentrantReadWriteLock bookLock = lockMap.computeIfAbsent(isbn, k -> new ReentrantReadWriteLock());
		if (isWriteLock) {
			bookLock.writeLock().lock();
		} else {
			bookLock.readLock().lock();
		}
	}


	private void releaseLocal(int isbn, boolean isWriteLock) {
		ReentrantReadWriteLock bookLock = lockMap.get(isbn);

		if (lockMap == null || (!bookMap.containsKey(isbn))) return;

		try {
			if (isWriteLock) {
				bookLock.writeLock().unlock();
			} else {
				bookLock.readLock().unlock();
			}
		} catch (IllegalMonitorStateException e) {
			// Handle case where lock wasn't held, but continue execution
		}
	}


	private void validate(StockBook book) throws BookStoreException {
		int isbn = book.getISBN();
		String bookTitle = book.getTitle();
		String bookAuthor = book.getAuthor();
		int noCopies = book.getNumCopies();
		float bookPrice = book.getPrice();

		if (BookStoreUtility.isInvalidISBN(isbn)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookTitle)) { // Check if the book has valid title
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isEmpty(bookAuthor)) { // Check if the book has valid author
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (BookStoreUtility.isInvalidNoCopies(noCopies)) { // Check if the book has at least one copy
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookPrice < 0.0) { // Check if the price of the book is valid
			throw new BookStoreException(BookStoreConstants.BOOK + book.toString() + BookStoreConstants.INVALID);
		}

		if (bookMap.containsKey(isbn)) {// Check if the book is not in stock
			throw new BookStoreException(BookStoreConstants.ISBN + isbn + BookStoreConstants.DUPLICATED);
		}
	}

	private void validate(BookCopy bookCopy) throws BookStoreException {
		int isbn = bookCopy.getISBN();
		int numCopies = bookCopy.getNumCopies();

		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock

		if (BookStoreUtility.isInvalidNoCopies(numCopies)) { // Check if the number of the book copy is larger than zero
			throw new BookStoreException(BookStoreConstants.NUM_COPIES + numCopies + BookStoreConstants.INVALID);
		}
	}

	private void validate(BookEditorPick editorPickArg) throws BookStoreException {
		int isbn = editorPickArg.getISBN();
		validateISBNInStock(isbn); // Check if the book has valid ISBN and in stock
	}

	private void validateISBNInStock(Integer ISBN) throws BookStoreException {
		if (BookStoreUtility.isInvalidISBN(ISBN)) { // Check if the book has valid ISBN
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
		}
		if (!bookMap.containsKey(ISBN)) {// Check if the book is in stock
			throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addBooks(java.util.Set)
	 */
	public void addBooks(Set<StockBook> bookSet) throws BookStoreException {
		if (bookSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		globalExclusiveLock.lock();

		try {
			// Check if all are there
			for (StockBook book : bookSet) {
				validate(book);
			}

			for (StockBook book : bookSet) {
				int isbn = book.getISBN();
				lockMap.put(isbn, new ReentrantReadWriteLock());
				bookMap.put(isbn, new BookStoreBook(book));
			}


		} finally {
			for (StockBook book : bookSet) {
				releaseLocal(book.getISBN(), true);
			}
			globalExclusiveLock.unlock();
		}

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#addCopies(java.util.Set)
	 */
	public void addCopies(Set<BookCopy> bookCopiesSet) throws BookStoreException {
		int isbn;
		int numCopies;

		if (bookCopiesSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		globalSharedLock.lock();

		try {
			for (BookCopy bookCopy : bookCopiesSet) {
				validate(bookCopy);
			}

			BookStoreBook book;


			// Update the number of copies
			for (BookCopy bookCopy : bookCopiesSet) {
				isbn = bookCopy.getISBN();
				lockLocal(isbn, true);
				numCopies = bookCopy.getNumCopies();
				book = bookMap.get(isbn);
				book.addCopies(numCopies);

			}
		} finally {
			for (BookCopy bookCopy : bookCopiesSet) {
				releaseLocal(bookCopy.getISBN(), true);
			}
			globalSharedLock.unlock();
		}

	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.StockManager#getBooks()
	 */
	public List<StockBook> getBooks() throws BookStoreException {
		globalSharedLock.lock();
		Collection<BookStoreBook> bookMapValues = bookMap.values();
		try {
			for (BookStoreBook book : bookMapValues) {
				lockLocal(book.getISBN(), false);
			}
			return bookMapValues.stream()
					.map(book -> book.immutableStockBook())
					.collect(Collectors.toList());
		}
		finally {
			for (BookStoreBook book : bookMapValues) {
				releaseLocal(book.getISBN(), false);
			}
			globalSharedLock.unlock();
		}


	}
	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#updateEditorPicks(java.util
	 * .Set)
	 */
	public void updateEditorPicks(Set<BookEditorPick> editorPicks) throws BookStoreException {
		// Check that all ISBNs that we add/remove are there first.
		if (editorPicks == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		int isbnValue;
		globalExclusiveLock.lock();

		try {
			for (BookEditorPick editorPickArg : editorPicks) {
				validate(editorPickArg);
			}
			for (BookEditorPick editorPickArg : editorPicks) {
				lockLocal(editorPickArg.getISBN(), true);
			}

			for (BookEditorPick editorPickArg : editorPicks) {
				bookMap.get(editorPickArg.getISBN()).setEditorPick(editorPickArg.isEditorPick());
			}
		} finally {
			for (BookEditorPick editorPickArg : editorPicks) {
				releaseLocal(editorPickArg.getISBN(), true);
			}
			globalExclusiveLock.unlock();
		}

	}




	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.BookStore#buyBooks(java.util.Set)
	 */
	public void buyBooks(Set<BookCopy> bookCopiesToBuy) throws BookStoreException {
		if (bookCopiesToBuy == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		globalSharedLock.lock();

		// Check that all ISBNs that we buy are there first.
		int isbn;
		BookStoreBook book;
		Boolean saleMiss = false;

		Map<Integer, Integer> salesMisses = new HashMap<>();

		try {
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				isbn = bookCopyToBuy.getISBN();

				validate(bookCopyToBuy);
				lockLocal(isbn, true);
				book = bookMap.get(isbn);

				if (!book.areCopiesInStore(bookCopyToBuy.getNumCopies())) {
					// If we cannot sell the copies of the book, it is a miss.
					salesMisses.put(isbn, bookCopyToBuy.getNumCopies() - book.getNumCopies());
					saleMiss = true;
				}
			}

			// We throw exception now since we want to see how many books in the
			// order incurred misses which is used by books in demand
			if (saleMiss) {
				for (Map.Entry<Integer, Integer> saleMissEntry : salesMisses.entrySet()) {
					book = bookMap.get(saleMissEntry.getKey());
					book.addSaleMiss(saleMissEntry.getValue());
				}
				throw new BookStoreException(BookStoreConstants.BOOK + BookStoreConstants.NOT_AVAILABLE);
			}

			// Then make the purchase.
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				book = bookMap.get(bookCopyToBuy.getISBN());
				book.buyCopies(bookCopyToBuy.getNumCopies());
			}

		} finally {
			for (BookCopy bookCopyToBuy : bookCopiesToBuy) {
				releaseLocal(bookCopyToBuy.getISBN(), true);
			}
			globalSharedLock.unlock();
		}




	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#getBooksByISBN(java.util.
	 * Set)
	 */
	public List<StockBook> getBooksByISBN(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		globalSharedLock.lock();
		try {
			for (Integer ISBN : isbnSet) {
				validateISBNInStock(ISBN);
				lockLocal(ISBN, false);
			}
			return isbnSet.stream()
					.map(isbn -> bookMap.get(isbn).immutableStockBook())
					.collect(Collectors.toList());
		} finally {
			for (Integer ISBN : isbnSet) {
				releaseLocal(ISBN, false);
			}
			globalSharedLock.unlock();
		}




	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.BookStore#getBooks(java.util.Set)
	 */
	public List<Book> getBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}
		globalSharedLock.lock();
		try {
			// Check that all ISBNs that we rate are there to start with.
			for (Integer ISBN : isbnSet) {
				validateISBNInStock(ISBN);
				lockLocal(ISBN, false);
			}

			return isbnSet.stream()
					.map(isbn -> bookMap.get(isbn).immutableBook())
					.collect(Collectors.toList());
		}
		finally {
			for (Integer ISBN : isbnSet) {
				releaseLocal(ISBN, false);
			}
			globalSharedLock.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.BookStore#getEditorPicks(int)
	 */
	public List<Book> getEditorPicks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be positive");
		}
		globalSharedLock.lock();
		try {
			List<BookStoreBook> listAllEditorPicks = bookMap.entrySet().stream()
					.map(pair -> pair.getValue())
					.filter(book -> book.isEditorPick())
					.collect(Collectors.toList());
			for (BookStoreBook book : listAllEditorPicks) {
				lockLocal(book.getISBN(), false);
			}


			// Find numBooks random indices of books that will be picked.
			Random rand = new Random();
			Set<Integer> tobePicked = new HashSet<>();
			int rangePicks = listAllEditorPicks.size();

			if (rangePicks <= numBooks) {

				// We need to add all books.
				for (int i = 0; i < listAllEditorPicks.size(); i++) {
					tobePicked.add(i);
				}
			} else {

				// We need to pick randomly the books that need to be returned.
				int randNum;

				while (tobePicked.size() < numBooks) {
					randNum = rand.nextInt(rangePicks);
					tobePicked.add(randNum);
				}
			}

			// Return all the books by the randomly chosen indices.
			return tobePicked.stream()
					.map(index -> listAllEditorPicks.get(index).immutableBook())
					.collect(Collectors.toList());
		} finally {
			for (BookStoreBook book : bookMap.values()) {
				releaseLocal(book.getISBN(), false);
			}
			globalSharedLock.unlock();
		}




	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.BookStore#getTopRatedBooks(int)
	 */
	@Override
	public List<Book> getTopRatedBooks(int numBooks) throws BookStoreException {
		if (numBooks < 0) {
			throw new BookStoreException("numBooks = " + numBooks + ", but it must be non-negative.");
		}

		globalSharedLock.lock();
		try {
			// Stream of books and filter those that have at least one rating
			List<BookStoreBook> ratedBooks = bookMap.values().stream()
					.filter(book -> book.getNumTimesRated() > 0)
					.sorted((b1, b2) -> {
						// Primary sorting by average rating (descending)
						double avgRating1 = (double) b1.getTotalRating() / b1.getNumTimesRated();
						double avgRating2 = (double) b2.getTotalRating() / b2.getNumTimesRated();
						int ratingCompare = Double.compare(avgRating2, avgRating1);

						// Secondary sorting by ISBN (ascending) if two books have the same average rating
						return (ratingCompare != 0) ? ratingCompare : Integer.compare(b1.getISBN(), b2.getISBN());
					})
					.limit(numBooks)
					.collect(Collectors.toList());

			for (BookStoreBook book : ratedBooks) {
				lockLocal(book.getISBN(), false);
			}

			// Convert to immutable books and return
			return ratedBooks.stream()
					.map(BookStoreBook::immutableBook)
					.collect(Collectors.toList());
		} finally {
			for (BookStoreBook book : bookMap.values()) {
				releaseLocal(book.getISBN(), false);
			}
			globalSharedLock.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.StockManager#getBooksInDemand()
	 */
	@Override
	public List<StockBook> getBooksInDemand() throws BookStoreException {
		globalSharedLock.lock();
		Collection<BookStoreBook> bookMapValues = bookMap.values();
		try {
			for (BookStoreBook book : bookMapValues) {
				lockLocal(book.getISBN(), false);
			}

			return bookMapValues.stream()
					.map(BookStoreBook::immutableStockBook)
					.filter(stockBook -> stockBook.getNumSaleMisses() > 0)
					.collect(Collectors.toList());
		} finally {
			for (BookStoreBook book : bookMapValues) {
				releaseLocal(book.getISBN(), false);
			}
			globalSharedLock.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.BookStore#rateBooks(java.util.Set)
	 */
	@Override
	public void rateBooks(Set<BookRating> bookRating) throws BookStoreException {
		if (bookRating == null || bookRating.isEmpty()) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		globalSharedLock.lock();
		try {
			// Step 1: Validate all ratings
			for (BookRating bookToRate : bookRating) {
				int isbn = bookToRate.getISBN();
				int rating = bookToRate.getRating();

				validateISBNInStock(isbn);
				lockLocal(isbn, true);

				if (BookStoreUtility.isInvalidRating(rating)) {
					throw new BookStoreException(BookStoreConstants.RATING + rating + BookStoreConstants.INVALID);
				}
			}

			// Step 2: Apply ratings only if all validations pass
			for (BookRating bookToRate : bookRating) {
				BookStoreBook book = bookMap.get(bookToRate.getISBN());
				book.addRating(bookToRate.getRating());
			}
		} finally {
			for (BookRating bookToRate : bookRating) {
				releaseLocal(bookToRate.getISBN(), true);
			}
			globalSharedLock.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.acertainbookstore.interfaces.StockManager#removeAllBooks()
	 */
	public void removeAllBooks() throws BookStoreException {
		globalExclusiveLock.lock();
		try {
			bookMap.clear();
			lockMap.clear();
		} finally {
			globalExclusiveLock.unlock();
		}
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see
	 * com.acertainbookstore.interfaces.StockManager#removeBooks(java.util.Set)
	 */
	public void removeBooks(Set<Integer> isbnSet) throws BookStoreException {
		if (isbnSet == null) {
			throw new BookStoreException(BookStoreConstants.NULL_INPUT);
		}

		globalExclusiveLock.lock();
		try {
			for (Integer ISBN : isbnSet) {
				if (BookStoreUtility.isInvalidISBN(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.INVALID);
				}

				if (!bookMap.containsKey(ISBN)) {
					throw new BookStoreException(BookStoreConstants.ISBN + ISBN + BookStoreConstants.NOT_AVAILABLE);
				}
			}
			for (Integer ISBN : isbnSet) {
				lockLocal(ISBN, true);
			}
			for (int isbn : isbnSet) {
				bookMap.remove(isbn);
			}
		} finally {
			for (Integer ISBN : isbnSet) {
				releaseLocal(ISBN, true);
			}
			globalExclusiveLock.unlock();
		}

	}
}
