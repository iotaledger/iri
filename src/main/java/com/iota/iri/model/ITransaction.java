package com.iota.iri.model;

import com.iota.iri.dataAccess.BufferPosition;
import com.iota.iri.dataAccess.DataAccessKey;
import com.iota.iri.dataAccess.StorageItem;

import java.nio.ByteBuffer;

/**
 * Created by paul on 3/2/17 for iri.
 */
public class ITransaction {
    public byte[] bytes;
    @DataAccessKey(index = 0)
    public int pointer;
    @DataAccessKey(index = 1)
    public byte[] hash;

    static	final	int	SIGNATURE_MESSAGE_OFFSET	=	0,	SIGNATURE_MESSAGE_LENGTH	=	19683;
    static	final	int	CHECKSUM_OFFSET	=	SIGNATURE_MESSAGE_OFFSET	+	SIGNATURE_MESSAGE_LENGTH,	CHECKSUM_LENGTH	=	243;
    static	final	int	ADDRESS_OFFSET	=	CHECKSUM_OFFSET	+	CHECKSUM_LENGTH,	ADDRESS_LENGTH	=	243;
    static	final	int	VALUE_OFFSET	=	ADDRESS_OFFSET	+	ADDRESS_LENGTH,	VALUE_LENGTH	=	81;
    static	final	int	TAG_OFFSET	=	VALUE_OFFSET	+	VALUE_LENGTH,	TAG_LENGTH	=	81;
    static	final	int	TIMESTAMP_OFFSET	=	TAG_OFFSET	+	TAG_LENGTH,	TIMESTAMP_LENGTH	=	81;
    static	final	int	TRUNK_OFFSET	=	TIMESTAMP_OFFSET	+	TIMESTAMP_LENGTH,	TRUNK_LENGTH	=	243;
    static	final	int	BRANCH_OFFSET	=	TRUNK_OFFSET	+	TRUNK_LENGTH,	BRANCH_LENGTH	=	243;
    static	final	int	NONCE_OFFSET	=	BRANCH_OFFSET	+	BRANCH_LENGTH,	NONCE_SIZE	=	243;
    public static final int TRINARY_SIZE = 8019;//NONCE_TRINARY_OFFSET + NONCE_TRINARY_SIZE;

    @BufferPosition(start = SIGNATURE_MESSAGE_OFFSET, length = SIGNATURE_MESSAGE_LENGTH)
    public ByteBuffer signature;
    @BufferPosition(start = CHECKSUM_OFFSET, length = CHECKSUM_LENGTH)
    public ByteBuffer checksum;
    @BufferPosition(start = ADDRESS_OFFSET, length = ADDRESS_LENGTH)
    public ByteBuffer address;
    @BufferPosition(start = VALUE_OFFSET, length = VALUE_LENGTH)
    public ByteBuffer value;
    @BufferPosition(start = TAG_OFFSET, length = TAG_LENGTH)
    public ByteBuffer tag;
    @BufferPosition(start = TIMESTAMP_OFFSET, length = TIMESTAMP_LENGTH)
    public ByteBuffer timestamp;
    @BufferPosition(start = TRUNK_OFFSET, length = TRUNK_LENGTH)
    public ByteBuffer trunk;
    @BufferPosition(start = BRANCH_OFFSET, length = BRANCH_LENGTH)
    public ByteBuffer branch;
    @BufferPosition(start = NONCE_OFFSET, length = NONCE_SIZE)
    public ByteBuffer nonce;

    @StorageItem
    public boolean isTip;
}
