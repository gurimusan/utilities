package org.gogoup.utilities.misc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Created by ruisun on 2016-10-29.
 */
public abstract class JDBCTransactionalService implements TransactionalService {

    private static final String TRANSACTION_COUNT = "_TRANSACTION_COUNT_";
    private static final String TRANSACTION_COMMIT_COUNT = "_TRANSACTION_COMMIT_COUNT_";
    private static final String TRANSACTION_ROLLBACK_COUNT = "_TRANSACTION_ROLLBACK_COUNT_";
    private static final String TRANSACTION_CONNECTION = "_TRANSACTION_CONNECTION_";

    private String name;
    private JDBCConnectionHelper helper;
    private TransactionContext txContext;


    public JDBCTransactionalService(String name, JDBCConnectionHelper helper) {
        this.name = name;
        this.helper = helper;
        this.txContext = null;
    }

    public JDBCTransactionalService(String name, DataSource dataSource) {
        this(name, new JDBCConnectionHelper(dataSource));
    }

    private JDBCConnectionHelper getHelper() {
        if (!isGlobalTransactionStarted()) {
            return helper;
        }
        JDBCConnectionHelper globalHelper = (JDBCConnectionHelper) txContext.getProperty(TRANSACTION_CONNECTION);
        if (null == globalHelper) {
            helper.startTransaction();
            txContext.setProperty(TRANSACTION_CONNECTION, helper);
        } else {
            helper = globalHelper;
        }
        return helper;
    }

    private boolean isGlobalTransactionStarted() {
        return (txContext != null);
    }

    private boolean isLocalTransactionStarted() {
        return getHelper().isTransactionStarted();
    }

    protected Connection getConnection() {
        return getHelper().getConnection();
    }

    private void checkForGlobalTransactionNotStarted() {
        if (!isGlobalTransactionStarted()) {
            throw new IllegalStateException("Need to start a transaction first!");
        }
    }

    private void checkForLocalTransactionNotStarted() {
        if (!isLocalTransactionStarted()) {
            throw new IllegalStateException("Need to start a nested transaction first!");
        }
    }

    protected void startLocalTransaction() {
        getHelper().startTransaction();
    }

    protected void commit() {
        checkForLocalTransactionNotStarted();
        if (isGlobalTransactionStarted()) {
            return;
        }
        getHelper().commit();
    }

    protected void rollback() {
        checkForLocalTransactionNotStarted();
        if (isGlobalTransactionStarted()) {
            throw new RuntimeException("Require rollback!"); //trigger global transaction rollback.
        }
        getHelper().rollback();
    }

    protected void closeResultSet(ResultSet rs) {
        helper.closeResultSet(rs);
    }

    protected void closePreparedStatement(PreparedStatement stmt) {
        helper.closePreparedStatement(stmt);
    }

    protected void closeConnection(Connection conn) {
        if (!isGlobalTransactionStarted()
                && !isLocalTransactionStarted()) {
            try {
                conn.close();
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void doStartTransaction(TransactionContext context, TransactionState transactionState)
            throws TransactionalServiceException {
        checkForLocalTransactionStarted();
        this.txContext = context;
        increaseTransactionCount();
    }

    private void checkForLocalTransactionStarted() {
        if (isLocalTransactionStarted()) {
            throw new IllegalStateException("Global transaction need started before a local transaction");
        }
    }

    private void increaseTransactionCount() {
        Integer count = (Integer) txContext.getProperty(TRANSACTION_COUNT);
        if (null == count) {
            count = 1;
        } else {
            count ++;
        }
        txContext.setProperty(TRANSACTION_COUNT, count);
    }

    private void markTransactionAsCommit() {
        Integer count = (Integer) txContext.getProperty(TRANSACTION_COMMIT_COUNT);
        if (null == count) {
            count = 1;
        } else {
            count ++;
        }
        txContext.setProperty(TRANSACTION_COMMIT_COUNT, count);
    }

    private void markTransactionAsRollback() {
        Integer count = (Integer) txContext.getProperty(TRANSACTION_ROLLBACK_COUNT);
        if (null == count) {
            count = 1;
        } else {
            count ++;
        }
        txContext.setProperty(TRANSACTION_ROLLBACK_COUNT, count);
    }

    private boolean isLastTransactionCall() {
        Integer count = (Integer) txContext.getProperty(TRANSACTION_COUNT);
        Integer commitCount = (Integer) txContext.getProperty(TRANSACTION_COMMIT_COUNT);
        Integer rollbackCount = (Integer) txContext.getProperty(TRANSACTION_ROLLBACK_COUNT);
        return ( count == (commitCount + 1) || count == (rollbackCount + 1) );
    }

    @Override
    public void doCommit(TransactionContext context, TransactionState transactionState)
            throws TransactionalServiceException {
        checkForGlobalTransactionNotStarted();
        if (isLastTransactionCall()) {
            getHelper().commit();
            txContext = null;
            markTransactionAsCommit();
        }
    }

    @Override
    public void doRollback(TransactionContext context, TransactionState transactionState)
            throws TransactionalServiceException {
        checkForGlobalTransactionNotStarted();
        if (isLastTransactionCall()) {
            getHelper().rollback();
            txContext = null;
            markTransactionAsRollback();
        }
    }

    @Override
    public void onError(TransactionContext transactionContext, TransactionState transactionState, Exception e) {

    }

}
