package com.acertainbookstore.client.tests;

import static org.junit.Assert.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.acertainbookstore.business.*;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.acertainbookstore.client.BookStoreHTTPProxy;
import com.acertainbookstore.client.StockManagerHTTPProxy;
import com.acertainbookstore.interfaces.BookStore;
import com.acertainbookstore.interfaces.StockManager;
import com.acertainbookstore.utils.BookStoreConstants;
import com.acertainbookstore.utils.BookStoreException;

/**
 * {@link BookStoreTest} tests the {@link BookStore} interface.
 * 
 * @see BookStore
 */
public class BookStoreTest {

	/** The Constant TEST_ISBN. */
	private static final int TEST_ISBN = 3044560;

	/** The Constant NUM_COPIES. */
	private static final int NUM_COPIES = 500;

	/** The local test. */
	private static boolean localTest = true;

	/** Single lock test */
	private static boolean singleLock = false;

	/** The store manager. */
	private static StockManager storeManager;

	/** The client. */
	private static BookStore client;

	/**
	 * Sets the up before class.
	 */
	@BeforeClass
	public static void setUpBeforeClass() {
		try {
			String localTestProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_LOCAL_TEST);
			localTest = (localTestProperty != null) ? Boolean.parseBoolean(localTestProperty) : localTest;
			
			String singleLockProperty = System.getProperty(BookStoreConstants.PROPERTY_KEY_SINGLE_LOCK);
			singleLock = (singleLockProperty != null) ? Boolean.parseBoolean(singleLockProperty) : singleLock;

			if (localTest) {
				if (singleLock) {
					SingleLockConcurrentCertainBookStore store = new SingleLockConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				} else {
					TwoLevelLockingConcurrentCertainBookStore store = new TwoLevelLockingConcurrentCertainBookStore();
					storeManager = store;
					client = store;
				}
			} else {
				storeManager = new StockManagerHTTPProxy("http://localhost:8081/stock");
				client = new BookStoreHTTPProxy("http://localhost:8081");
			}

			storeManager.removeAllBooks();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Helper method to add some books.
	 *
	 * @param isbn
	 *            the isbn
	 * @param copies
	 *            the copies
	 * @throws BookStoreException
	 *             the book store exception
	 */
	public void addBooks(int isbn, int copies) throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		StockBook book = new ImmutableStockBook(isbn, "Test of Thrones", "George RR Testin'", (float) 10, copies, 0, 0,
				0, false);
		booksToAdd.add(book);
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Helper method to get the default book used by initializeBooks.
	 *
	 * @return the default book
	 */
	public StockBook getDefaultBook() {
		return new ImmutableStockBook(TEST_ISBN, "Harry Potter and JUnit", "JK Unit", (float) 10, NUM_COPIES, 0, 0, 0,
				false);
	}

	/**
	 * Method to add a book, executed before every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Before
	public void initializeBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(getDefaultBook());
		storeManager.addBooks(booksToAdd);
	}

	/**
	 * Method to clean up the book store, execute after every test case is run.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@After
	public void cleanupBooks() throws BookStoreException {
		storeManager.removeAllBooks();
	}

	/**
	 * Tests basic buyBook() functionality.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyAllCopiesDefaultBook() throws BookStoreException {
		// Set of books to buy
		Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES));

		// Try to buy books
		client.buyBooks(booksToBuy);

		List<StockBook> listBooks = storeManager.getBooks();
		assertTrue(listBooks.size() == 1);
		StockBook bookInList = listBooks.get(0);
		StockBook addedBook = getDefaultBook();

		assertTrue(bookInList.getISBN() == addedBook.getISBN() && bookInList.getTitle().equals(addedBook.getTitle())
				&& bookInList.getAuthor().equals(addedBook.getAuthor()) && bookInList.getPrice() == addedBook.getPrice()
				&& bookInList.getNumSaleMisses() == addedBook.getNumSaleMisses()
				&& bookInList.getAverageRating() == addedBook.getAverageRating()
				&& bookInList.getNumTimesRated() == addedBook.getNumTimesRated()
				&& bookInList.getTotalRating() == addedBook.getTotalRating()
				&& bookInList.isEditorPick() == addedBook.isEditorPick());
	}

	/**
	 * Tests that books with invalid ISBNs cannot be bought.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyInvalidISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with invalid ISBN.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(-1, 1)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that books can only be bought if they are in the book store.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNonExistingISBN() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a book with ISBN which does not exist.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, 1)); // valid
		booksToBuy.add(new BookCopy(100000, 10)); // invalid

		// Try to buy the books.
		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();

		// Check pre and post state are same.
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy more books than there are copies.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyTooManyBooks() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy more copies than there are in store.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, NUM_COPIES + 1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that you can't buy a negative number of books.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testBuyNegativeNumberOfBookCopies() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Try to buy a negative number of copies.
		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.buyBooks(booksToBuy);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Tests that all books can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetBooks() throws BookStoreException {
		Set<StockBook> booksAdded = new HashSet<StockBook>();
		booksAdded.add(getDefaultBook());

		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		booksAdded.addAll(booksToAdd);

		storeManager.addBooks(booksToAdd);

		// Get books in store.
		List<StockBook> listBooks = storeManager.getBooks();

		// Make sure the lists equal each other.
		assertTrue(listBooks.containsAll(booksAdded) && listBooks.size() == booksAdded.size());
	}

	/**
	 * Tests that a list of books with a certain feature can be retrieved.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetCertainBooks() throws BookStoreException {
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 1, "The Art of Computer Programming", "Donald Knuth",
				(float) 300, NUM_COPIES, 0, 0, 0, false));
		booksToAdd.add(new ImmutableStockBook(TEST_ISBN + 2, "The C Programming Language",
				"Dennis Ritchie and Brian Kerninghan", (float) 50, NUM_COPIES, 0, 0, 0, false));

		storeManager.addBooks(booksToAdd);

		// Get a list of ISBNs to retrieved.
		Set<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN + 1);
		isbnList.add(TEST_ISBN + 2);

		// Get books with that ISBN.
		List<Book> books = client.getBooks(isbnList);

		// Make sure the lists equal each other
		assertTrue(books.containsAll(booksToAdd) && books.size() == booksToAdd.size());
	}

	/**
	 * Tests that books cannot be retrieved if ISBN is invalid.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@Test
	public void testGetInvalidIsbn() throws BookStoreException {
		List<StockBook> booksInStorePreTest = storeManager.getBooks();

		// Make an invalid ISBN.
		HashSet<Integer> isbnList = new HashSet<Integer>();
		isbnList.add(TEST_ISBN); // valid
		isbnList.add(-1); // invalid

		HashSet<BookCopy> booksToBuy = new HashSet<BookCopy>();
		booksToBuy.add(new BookCopy(TEST_ISBN, -1));

		try {
			client.getBooks(isbnList);
			fail();
		} catch (BookStoreException ex) {
			;
		}

		List<StockBook> booksInStorePostTest = storeManager.getBooks();
		assertTrue(booksInStorePreTest.containsAll(booksInStorePostTest)
				&& booksInStorePreTest.size() == booksInStorePostTest.size());
	}

	/**
	 * Two clients C1 and C2, running in different threads, each invoke a fixed number
	 * of operations, configured as a parameter, against the BookStore and StockManager
	 * interfaces. Both C1 and C2 operate against the same set of books S. C1 calls
	 * buyBooks, while C2 calls addCopies on S. The store’s initial state should have a
	 * sufficient number of copies in stock to execute the fixed number of operations without
	 * exceptions. The test’s net result should be that the books in S end with the same
	 * number of copies in stock as they started. Otherwise, the test fails, indicating that
	 * operations that perform conflicting writes to S were not atomic.
	 * @throws InterruptedException, BookStoreException
	 */
	@Test
	public void requiredConcurrencyTest1() throws BookStoreException, InterruptedException {
		int ITERATIONS = 1000;

		Thread clientThread = new Thread(() -> {
			try {
				Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
				booksToBuy.add(new BookCopy(TEST_ISBN, 1));
				for (int i = 0; i < ITERATIONS; i++) {
					client.buyBooks(booksToBuy);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
				fail();
			}
		});

		Thread storeManagerThread = new Thread(() -> {
			try {
				Set<BookCopy> booksToAdd = new HashSet<BookCopy>();
				booksToAdd.add(new BookCopy(TEST_ISBN, 1));
				for (int i = 0; i < ITERATIONS; i++) {
					storeManager.addCopies(booksToAdd);
				}
			} catch (BookStoreException e) {
				e.printStackTrace();
				fail();
			}
		});

		clientThread.start();
		storeManagerThread.start();

		clientThread.join();
		storeManagerThread.join();

		try {
			List<StockBook> books = storeManager.getBooks();
			for (StockBook book : books) {
				if (book.getISBN() == TEST_ISBN) {
					assertEquals(NUM_COPIES, book.getNumCopies());
				}
			}
		} catch (BookStoreException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Two clients C1 and C2, running in different threads, continuously invoke oper-
	 * ations against the BookStore and StockManager interfaces. C1 invokes buyBooks to
	 * buy a given and fixed collection of books (e.g., the Star Wars trilogy). C1 then invokes
	 * addCopies to replenish the stock of exactly the same books bought. C2 continuously
	 * calls getBooks and ensures that the snapshot returned either has the quantities for all
	 * of these books as if they had been just bought or as if they had been just replenished.
	 * In other words, the snapshots returned by getBooks must be consistent. The test fails
	 * if any inconsistent snapshot is observed, and succeeds after a large enough number of
	 * invocations of getBooks, configured as a parameter to the test.
	 * @throws InterruptedException
	 */
	@Test
	public void requiredConcurrencyTest2() throws InterruptedException {
		int ITERATIONS = 150;
		Set<StockBook> booksToAdd = new HashSet<StockBook>();
		for (int i = 1; i <= 100; i++) {
			booksToAdd.add(
					new ImmutableStockBook(
							i,
							"Title",
							"Author",
							1f, 1, 0, 0, 0, false));
		}
		final AtomicBoolean[] concurrentTestHasFailed = {new AtomicBoolean(false)};

		try {
			// Add books to store
			storeManager.addBooks(booksToAdd);

			// Prepare books to buy
			Set<BookCopy> booksToBuy = booksToAdd.stream()
					.map(book -> new BookCopy(book.getISBN(), 1))
					.collect(Collectors.toSet());

			Thread C1Thread = new Thread() {
				public void run() {
					for (int i = 0; i < ITERATIONS; i++) {
						try {
							client.buyBooks(booksToBuy);
							storeManager.addCopies(booksToBuy);
						} catch (BookStoreException e) {
							concurrentTestHasFailed[0].set(true);
							e.printStackTrace();
						}
					}
				}
			};

			// Thread C2: Check consistency of book snapshots
			Thread C2Thread = new Thread(() -> {
				try {
					for (int i = 0; i < ITERATIONS; i++) {
						List<StockBook> snapshot = storeManager.getBooks();

						// We check for inconsistencies in the snapshot
						// By comparing bought and replenishes states
						boolean allBoughtState = true;
						boolean allReplenishedState = true;

						for (StockBook book : snapshot) {
							if (book.getISBN() > 100) continue; // Ignore irrelevant books

							int currentAmount = book.getNumCopies();
							if (currentAmount == 1) {
								allBoughtState = false;
							} else if (currentAmount == 0) {
								allReplenishedState = false;
							} else {
								concurrentTestHasFailed[0].set(true);
								fail("Inconsistent state detected for book ISBN: " + book.getISBN());
							}
						}

						// If none of the iterations give an inconsistent snapshot,
						// It will not fail and hence pass the test
						if (!allBoughtState && !allReplenishedState) {
							concurrentTestHasFailed[0].set(true);
							fail("Snapshot is inconsistent");
						}
					}
				} catch (BookStoreException e) {
					e.printStackTrace();
					concurrentTestHasFailed[0].set(true);
					fail("Exception in C2: " + e.getMessage());

				}
			});

			// Starting both client threads
			C1Thread.start();
			C2Thread.start();

			// Waiting for threads to finish
			C1Thread.join();
			C2Thread.join();
		} catch (BookStoreException e) {
			e.printStackTrace();
			fail("Exception during setup: " + e.getMessage());
		}
		assertFalse(concurrentTestHasFailed[0].get());
	}

	/**
	 * Test which tests if a main thread C1 both adds books and removes them right after, while a thread C2 continuously
	 * queries getBooks to check if the amount of books in store is consistent. This means we either expect it to
	 * be the initial value which we keep adding or 0, since we removed it from stock. The test succeeds after a specific amount of
	 * iterations have been gone through without an error. It is set high to catch potential errors.
	 * It fails when snapshot from getBooks is inconsistent.
	 * @throws InterruptedException
	 */
	@Test
	public void additionalConcurrencyTest3() throws InterruptedException {
		final int ITERATIONS = 10000;
		final int BOOKS_TOTAL = 10;

		AtomicBoolean hasFailed = new AtomicBoolean(false);
		// First thread adds and removes books
		Thread C1Thread = new Thread(() -> {
			try {
				Set<StockBook> booksToAdd = new HashSet<StockBook>();
				for (int i = 1; i <= BOOKS_TOTAL; i++) {
					booksToAdd.add(new ImmutableStockBook(i, "Title", "Author", 1f, NUM_COPIES, 0, 0, 0, false));
				}
				Set<Integer> booksToRemove = new HashSet<Integer>();
				for (int i = 1; i <= BOOKS_TOTAL; i++) {
					booksToRemove.add(i);
				}
				for (int i = 0; i < ITERATIONS; i++) {
					storeManager.addBooks(booksToAdd);
					storeManager.removeBooks(booksToRemove);
				}
			} catch (Exception e) {
				hasFailed.set(true);
				e.printStackTrace();
				fail("failed");
			}
		});

		// Second thread repeatedly calls getBooks
		Thread C2Thread = new Thread(() -> {
			try {
				for (int i = 0; i < ITERATIONS; i++) {
					List<StockBook> snapshot = storeManager.getBooks();

					for (StockBook book : snapshot) {
						// We assume that either all copies are there, or book is deleted.
						// Because of locks we never expect to get a different case in which e.g. the numCopies is higher
						// than expected since we have added but not removed yet
						if (book.getISBN() == 1) {
							assertTrue(NUM_COPIES == book.getNumCopies() || 0 == book.getNumCopies());
						} else if (book.getISBN() == 2) {
							assertTrue(NUM_COPIES == book.getNumCopies() || 0 == book.getNumCopies());
						} else if (book.getISBN() == 3) {
							assertTrue(NUM_COPIES == book.getNumCopies() || 0 == book.getNumCopies());
						}
					}
				}
			} catch (Exception e) {
				hasFailed.set(true);
				e.printStackTrace();
				fail();
			}
		});

		// Start threads simultaneously
		C1Thread.start();
		C2Thread.start();

		// Wait for threads to finish
		C1Thread.join();
		C2Thread.join();

		assertFalse(hasFailed.get());
	}

	/**
	 * This test case tests a scenario where multiple threads are trying
	 * to both read the stock and modify it at the same time.
	 * This test is supposed to test for potential issues like inconsistent reads or
	 * race conditions between getBooks and addCopies or buyBooks.
	 * @throws BookStoreException
	 * @throws InterruptedException
	 */
	@Test
	public void additionalConcurrencyTest4() throws BookStoreException, InterruptedException {
		int ITERATIONS = 100;
		int NUM_CLIENT_THREADS = 5;
		int NUM_STORE_MANAGER_THREADS = 5;
		int INITIAL_COPIES = 5000;

		// Set initial stock for the book
		Set<BookCopy> initialStock = new HashSet<>();
		initialStock.add(new BookCopy(TEST_ISBN, INITIAL_COPIES));
		storeManager.addCopies(initialStock); // Ensure the initial stock is set up properly

		// Create and start multiple client threads buying books
		Thread[] clientThreads = new Thread[NUM_CLIENT_THREADS];
		for (int i = 0; i < NUM_CLIENT_THREADS; i++) {
			clientThreads[i] = new Thread(() -> {
				try {
					Set<BookCopy> booksToBuy = new HashSet<BookCopy>();
					booksToBuy.add(new BookCopy(TEST_ISBN, 1));
					for (int j = 0; j < ITERATIONS; j++) {
						client.buyBooks(booksToBuy);
					}
				} catch (BookStoreException e) {
					e.printStackTrace();
					fail();
				}
			});
			clientThreads[i].start();
		}

		// Create and start multiple store manager threads adding copies
		Thread[] storeManagerThreads = new Thread[NUM_STORE_MANAGER_THREADS];
		for (int i = 0; i < NUM_STORE_MANAGER_THREADS; i++) {
			storeManagerThreads[i] = new Thread(() -> {
				try {
					Set<BookCopy> booksToAdd = new HashSet<BookCopy>();
					booksToAdd.add(new BookCopy(TEST_ISBN, 1));
					for (int j = 0; j < ITERATIONS; j++) {
						storeManager.addCopies(booksToAdd); // Store managers add copies
					}
				} catch (BookStoreException e) {
					e.printStackTrace();
					fail();
				}
			});
			storeManagerThreads[i].start();
		}

		// Create and start multiple threads to query the stock concurrently
		Thread[] queryThreads = new Thread[NUM_STORE_MANAGER_THREADS];
		for (int i = 0; i < NUM_STORE_MANAGER_THREADS; i++) {
			queryThreads[i] = new Thread(() -> {
				try {
					for (int j = 0; j < ITERATIONS; j++) {
						storeManager.getBooks(); // Query the stock
					}
				} catch (BookStoreException e) {
					e.printStackTrace();
					fail();
				}
			});
			queryThreads[i].start();
		}

		// Wait for all client threads to complete
		for (int i = 0; i < NUM_CLIENT_THREADS; i++) {
			clientThreads[i].join();
		}

		// Wait for all store manager threads to complete
		for (int i = 0; i < NUM_STORE_MANAGER_THREADS; i++) {
			storeManagerThreads[i].join();
		}

		// Wait for all query threads to complete
		for (int i = 0; i < NUM_STORE_MANAGER_THREADS; i++) {
			queryThreads[i].join();
		}

		// Verify that the total stock count is correct after all operations
		try {
			List<StockBook> books = storeManager.getBooks();
			for (StockBook book : books) {
				if (book.getISBN() == TEST_ISBN) {
					int expectedStock = INITIAL_COPIES + (NUM_STORE_MANAGER_THREADS * ITERATIONS) - (NUM_CLIENT_THREADS * ITERATIONS);
					// We add 500 due to default book raising expected
					assertEquals("The number of copies should match the expected value", expectedStock + 500, book.getNumCopies());
				}
			}
		} catch (BookStoreException e) {
			e.printStackTrace();
			fail();
		}
	}

	/**
	 * Tests concurrency through editor pick toggeling. This allows us to further test
	 * concurrency correctness of the BookStore interface
	 * We have two threads C1 and C2, C1 will run and toggle the editor status for a set of books
	 * while C2 will retrieve the editors picks and validate if it is consistent.
	 * I.e. if all books are either editors picks or not. Since we initialize all as false,
	 * we should never get a mix of true and false
	 * @throws InterruptedException
	 */
	@Test
	public void additionalConcurrencyTest5() throws InterruptedException {
		final int ITERATIONS = 150;
		final int NUM_BOOKS = 50;

		Set<StockBook> booksToAdd = new HashSet<>();
		for (int i = 1; i <= NUM_BOOKS; i++) {
			booksToAdd.add(
					new ImmutableStockBook(i, "Title", "Author",1f, 5, 0, 0, 0, false));
		}

		final AtomicBoolean concurrentTestHasFailed = new AtomicBoolean(false);

		try {
			// Add books to store
			storeManager.addBooks(booksToAdd);

			// Prepare editor pick toggles
			Set<BookEditorPick> toggleEditorPicksTrue = booksToAdd.stream()
					.map(book -> new BookEditorPick(book.getISBN(), true))
					.collect(Collectors.toSet());

			Set<BookEditorPick> toggleEditorPicksFalse = booksToAdd.stream()
					.map(book -> new BookEditorPick(book.getISBN(), false))
					.collect(Collectors.toSet());

			// Thread C1: Toggles editor pick status
			Thread C1Thread = new Thread(() -> {
				try {
					for (int i = 0; i < ITERATIONS; i++) {
						// Set all to true
						storeManager.updateEditorPicks(toggleEditorPicksTrue);
						// Set all to false
						storeManager.updateEditorPicks(toggleEditorPicksFalse);
					}
				} catch (BookStoreException e) {
					concurrentTestHasFailed.set(true);
					e.printStackTrace();
				}
			});

			// Thread C2: Retrieves editor picks and validates consistency
			Thread C2Thread = new Thread(() -> {
				try {
					for (int i = 0; i < ITERATIONS * 2; i++) { // Twice the iterations to match toggles
						List<Book> editorPicks = client.getEditorPicks(NUM_BOOKS);

						// Validate snapshot
						boolean allTrue = editorPicks.size() == NUM_BOOKS;
						boolean allFalse = editorPicks.isEmpty();

						if (!allTrue && !allFalse) {
							concurrentTestHasFailed.set(true);
							fail("Inconsistent editor pick snapshot observed");
						}
					}
				} catch (BookStoreException e) {
					concurrentTestHasFailed.set(true);
					e.printStackTrace();
					fail("Exception in C2: " + e.getMessage());
				}
			});

			// Start both threads
			C1Thread.start();
			C2Thread.start();

			// Wait for threads to finish
			C1Thread.join();
			C2Thread.join();

		} catch (BookStoreException e) {
			e.printStackTrace();
			fail("Exception during setup: " + e.getMessage());
		}

		// Ensure no failures occurred during the test
		assertFalse(concurrentTestHasFailed.get());
	}


	/**
	 * Tear down after class.
	 *
	 * @throws BookStoreException
	 *             the book store exception
	 */
	@AfterClass
	public static void tearDownAfterClass() throws BookStoreException {
		storeManager.removeAllBooks();

		if (!localTest) {
			((BookStoreHTTPProxy) client).stop();
			((StockManagerHTTPProxy) storeManager).stop();
		}
	}


}
