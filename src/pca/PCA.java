package pca;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;

import util.Log;
import util.Log.LogType;

public abstract class PCA {

	protected DenseMatrix64F data;
	private DenseMatrix64F mean;
	private boolean dataLock; /* If the data already has been centered. */
	private boolean meanDirty = true;
	protected int numComponents;
	protected DenseMatrix64F basis = null;

	public PCA() {
		this.data = null;
		this.mean = null;
		this.dataLock = false;
	}

	/** Create a new PCA with the provided data, with one row = one sample. */
	public PCA(DenseMatrix64F data) {
		this.data = data;
		this.mean = null;
		this.dataLock = false;
	}

	/** Add a new sample in the PCA. The first sample added decide the sample's size.
	 * If further add is done with a different size, an exception is throw.
	 */
	public void addSample(DenseMatrix64F sample) {
		if(dataLock)
			throw new RuntimeException("Data already locked.");

		if(data == null) {
			data = sample;
		}
		else {
			if(data.numCols != sample.numCols)
				throw new RuntimeException("Unexpected sample length.");
			data.reshape(data.numRows + 1, data.numCols, true);

			for(int i = 0; i < data.numCols; i++) {
				data.set(data.numRows - 1, i, sample.get(i));
			}
		}
	}

	/** Compute the basis matrix. */
	public void computeBasis(int numComponents) {
		this.numComponents = numComponents;
		centerData();
		doComputeBasis();
	}

	/** This method should compute the basis matrix. */
	protected abstract void doComputeBasis();

	/** @return the basis vector. */
	public DenseMatrix64F getBasis() {
		ensureBasis();
		return basis;
	}

	/** @return a vector of the feature space basis. */
	public double[] getBasisVector(int index) {
		ensureBasis();
		return getCols(basis, index).data;
	}

	/** @return the number of component of the feature space. */
	public int getNumComponents() {
		ensureBasis();
		return numComponents;
	}

	public int getSampleSize() {
		if(data == null)
			return 0;
		return data.numCols;
	}

	/** @return a sample from the original data expressed in the feature space.
	 *  @param index the index of the sample in the original data.
	 */
	public double[] sampleToFeatureSpace(int index) {
		return sampleToFeatureSpaceNoMean(getRow(data, index));
	}

	/** @return an arbitrary sample expressed in the feature space.
	 *  @param sample the sample data. Length should be the same as the original data.
	 */
	public double[] sampleToFeatureSpace(double[] sample) {
		ensureMean();

		DenseMatrix64F sampleMatrix = DenseMatrix64F.wrap(1, data.numCols, sample);

		/* Subtract the mean from the sample. */
		CommonOps.sub(sampleMatrix, mean, sampleMatrix);

		return sampleToFeatureSpaceNoMean(sampleMatrix);
	}

	/** Internal sampleToFeatureSpace, for data that are already mean subtracted.
	 *  @param sample the sample to convert.
	 */
	private double[] sampleToFeatureSpaceNoMean(DenseMatrix64F sample) {
		ensureBasis();

		if(sample.numCols != data.numCols)
			throw new IllegalArgumentException("Unexpected sample length.");

		DenseMatrix64F result = new DenseMatrix64F(numComponents, 1);

		/* Use the basis matrix to go to the feature space. */
		CommonOps.mult(basis, sample, result);
		return result.data;
	}

	/** @return an arbitrary sample expressed in the sample space.
	 *  @param sample the sample data. Length should be the same as the feature space dimension.
	 */
	public double[] sampleToSampleSpace(double[] sample) {
		if(sample.length != numComponents)
			throw new IllegalArgumentException("Unexpected sample length.");

		ensureMean();

		DenseMatrix64F sampleMatrix = DenseMatrix64F.wrap(numComponents, 1, sample);
		DenseMatrix64F result = new DenseMatrix64F(1, data.numCols);

		CommonOps.multTransA(basis, sampleMatrix, result);
		CommonOps.add(result, mean, result);
		return result.data;
	}

	/** Compute the error resulting for a projection to feature space and back for a sample.
	 *  This could be used to test the membership of a sample to the feature space.
	 */
	public double errorMembership(double[] sample) {
		double[] feat = sampleToFeatureSpace(sample);
		double[] back = sampleToSampleSpace(feat);

		double total = 0.0;
		for(int i = 0; i < back.length; i++) {
			double error = sample[i] - back[i];
			total += error * error;
		}

		return Math.sqrt(total / back.length);
	}

	public DenseMatrix64F getMean() {
		ensureMean();
		return mean;
	}

	/** Update the mean sample of the original data. */
	private void computeMean() {
		Log.debug(LogType.MODEL, "PCA: compute mean sample.");

		if(mean == null)
			mean = new DenseMatrix64F(1, data.numCols);
		else
			mean.zero();

		for(int i = 0; i < data.numRows; i++)
			CommonOps.add(mean, getRow(data,i), mean);

		CommonOps.divide(data.numRows, mean);
		meanDirty = false;
	}

	/** Subtract the mean from all samples. */
	private void centerData() {
		Log.debug(LogType.MODEL, "PCA: lock data.");
		ensureMean();
		for(int i = 0; i < data.numRows; i++)
			for(int j = 0; j < data.numCols; j++)
				data.set(i, j, data.get(i, j) - mean.get(i));
		this.dataLock = true;
	}

	/** @return a row of the matrix. */
	private DenseMatrix64F getRow(DenseMatrix64F matrix, int index) {
		if(index < 0 || index >= matrix.numRows)
			throw new IllegalArgumentException("Unexpected index.");

		return CommonOps.extract(matrix, 0, matrix.numCols, index, index +1);
	}

	/** @return a column of the matrix. */
	private DenseMatrix64F getCols(DenseMatrix64F matrix, int index) {
		if(index < 0 || index >= matrix.numCols)
			throw new IllegalArgumentException("Unexpected index.");

		return CommonOps.extract(matrix, index, index + 1, 0, matrix.numRows);
	}

	/** If no explicit computeBasis call have been made with a numComponents,
	 *  we compute the PCA with the same dimension as the original data. */
	private void ensureBasis() {
		if(basis == null)
			computeBasis(data.numRows);
	}

	/** Ensure that the mean is properly computed. */
	private void ensureMean() {
		if(meanDirty)
			computeMean();
	}
}
