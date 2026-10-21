/*
 * Copyright 2018 m2049r
 * Copyright 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.m2049r.xmrwallet.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Calendar;
import java.util.GregorianCalendar;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.security.auth.x500.X500Principal;

import timber.log.Timber;

public class KeyStoreHelper {

    static {
        System.loadLibrary("monerujo");
    }

    public static native byte[] slowHash(byte[] data, int brokenVariant);

    static final private String RSA_ALIAS = "MonerujoRSA";
    static final private String WALLET_PASS_KEY_PREFIX_V2 = "walletKeyV2-";
    static final private String WALLET_PASS_CIPHERTEXT_V2 = "v2:";

    private static String getCrazyPass(Context context, String password, int brokenVariant) {
        byte[] data = password.getBytes(StandardCharsets.UTF_8);
        byte[] sig = null;
        try {
            KeyStoreHelper.createKeys(context, RSA_ALIAS);
            sig = KeyStoreHelper.signData(RSA_ALIAS, data);
            byte[] hash = slowHash(sig, brokenVariant);
            if (hash == null) {
                throw new IllegalStateException("Slow Hash is null!");
            }
            return CrazyPassEncoder.encode(hash);
        } catch (NoSuchProviderException | NoSuchAlgorithmException |
                InvalidAlgorithmParameterException | KeyStoreException |
                InvalidKeyException | SignatureException ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static String getCrazyPass(Context context, String password) {
        if (Helper.useCrazyPass(context))
            return getCrazyPass(context, password, 0);
        else
            return password;
    }

    public static String getBrokenCrazyPass(Context context, String password, int brokenVariant) {
        // due to a link bug in the initial implementation, some crazypasses were built with
        // prehash & variant == 1
        // since there are wallets out there, we need to keep this here
        // yes, it's a mess
        if (isArm32() && (brokenVariant != 2)) return null;
        return getCrazyPass(context, password, brokenVariant);
    }

    private static Boolean isArm32 = null;

    public static boolean isArm32() {
        if (isArm32 != null) return isArm32;
        synchronized (KeyStoreException.class) {
            if (isArm32 != null) return isArm32;
            isArm32 = Build.SUPPORTED_ABIS[0].equals("armeabi-v7a");
            return isArm32;
        }
    }

    public static boolean saveWalletUserPass(@NonNull Context context, String wallet, String password) {
        String walletKeyAlias = WALLET_PASS_KEY_PREFIX_V2 + wallet;
        byte[] data = password.getBytes(StandardCharsets.UTF_8);
        try {
            KeyStoreHelper.createKeys(context, walletKeyAlias);
            byte[] encrypted = KeyStoreHelper.encrypt(walletKeyAlias, data);
            SharedPreferences.Editor e = context.getSharedPreferences(SecurityConstants.WALLET_PASS_PREFS_NAME, Context.MODE_PRIVATE).edit();
            if (encrypted == null) {
                return false;
            }
            e.putString(wallet, WALLET_PASS_CIPHERTEXT_V2
                    + Base64.encodeToString(encrypted, Base64.DEFAULT)).apply();
            deleteKeys(SecurityConstants.WALLET_PASS_KEY_PREFIX + wallet);
            return true;
        } catch (NoSuchProviderException | NoSuchAlgorithmException |
                InvalidAlgorithmParameterException | KeyStoreException ex) {
            Timber.w(ex);
            return false;
        }
    }

    static public class BrokenPasswordStoreException extends Exception {
        BrokenPasswordStoreException() {
            super();
        }

        BrokenPasswordStoreException(Throwable cause) {
            super(cause);
        }
    }

    public static boolean hasStoredPasswords(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SecurityConstants.WALLET_PASS_PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getAll().size() > 0;
    }

    public static String loadWalletUserPass(@NonNull Context context, String wallet) throws BrokenPasswordStoreException {
        return decryptWalletUserPass(context, wallet);
    }

    public static Cipher getWalletUserPassCipher(@NonNull Context context, String wallet)
            throws BrokenPasswordStoreException {
        String encoded = getStoredWalletUserPass(context, wallet);
        if (encoded.isEmpty()) throw new BrokenPasswordStoreException();
        boolean version2 = encoded.startsWith(WALLET_PASS_CIPHERTEXT_V2);
        String alias = (version2 ? WALLET_PASS_KEY_PREFIX_V2
                : SecurityConstants.WALLET_PASS_KEY_PREFIX) + wallet;
        String transformation = version2 ? SecurityConstants.CIPHER_RSA_ECB_OAEP
                : SecurityConstants.CIPHER_RSA_ECB_PKCS1;
        try {
            Cipher cipher = Cipher.getInstance(transformation);
            cipher.init(Cipher.DECRYPT_MODE, getPrivateKey(alias));
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException ex) {
            throw new BrokenPasswordStoreException(ex);
        }
    }

    public static String decryptWalletUserPass(Context context, String wallet, Cipher cipher)
            throws BrokenPasswordStoreException {
        String encoded = getStoredWalletUserPass(context, wallet);
        if (encoded.startsWith(WALLET_PASS_CIPHERTEXT_V2))
            encoded = encoded.substring(WALLET_PASS_CIPHERTEXT_V2.length());
        try {
            return new String(cipher.doFinal(Base64.decode(encoded, Base64.DEFAULT)),
                    StandardCharsets.UTF_8);
        } catch (IllegalBlockSizeException | BadPaddingException | RuntimeException ex) {
            throw new BrokenPasswordStoreException(ex);
        }
    }

    private static String getStoredWalletUserPass(Context context, String wallet) {
        return context.getSharedPreferences(SecurityConstants.WALLET_PASS_PREFS_NAME, Context.MODE_PRIVATE)
                .getString(wallet, "");
    }

    private static String decryptWalletUserPass(Context context, String wallet)
            throws BrokenPasswordStoreException {
        String encoded = getStoredWalletUserPass(context, wallet);
        if (encoded.isEmpty()) throw new BrokenPasswordStoreException();
        Cipher cipher = getWalletUserPassCipher(context, wallet);
        return decryptWalletUserPass(context, wallet, cipher);
    }

    public static boolean hasWalletUserPass(@NonNull Context context, String wallet) {
        if (getStoredWalletUserPass(context, wallet).isEmpty()) return false;
        try {
            return keyExists(wallet);
        } catch (BrokenPasswordStoreException ex) {
            return false;
        }
    }

    public static void removeWalletUserPass(Context context, String wallet) {
        String walletKeyAlias = SecurityConstants.WALLET_PASS_KEY_PREFIX + wallet;
        try {
            KeyStoreHelper.deleteKeys(walletKeyAlias);
        } catch (KeyStoreException ex) {
            Timber.w(ex);
        }
        try {
            deleteKeys(WALLET_PASS_KEY_PREFIX_V2 + wallet);
        } catch (KeyStoreException ex) {
            Timber.w(ex);
        }
        context.getSharedPreferences(SecurityConstants.WALLET_PASS_PREFS_NAME, Context.MODE_PRIVATE).edit()
                .remove(wallet).apply();
    }

    public static void copyWalletUserPass(Context context, String srcWallet, String dstWallet) throws BrokenPasswordStoreException {
        final String pass = loadWalletUserPass(context, srcWallet);
        saveWalletUserPass(context, dstWallet, pass);
    }

    /**
     * Creates a public and private key and stores it using the Android Key
     * Store, so that only this application will be able to access the keys.
     */
    private static void createKeys(Context context, String alias) throws NoSuchProviderException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
        try {
            keyStore.load(null);
        } catch (IOException | CertificateException ex) {
            throw new IllegalStateException("Could not load KeySotre", ex);
        }
        if (!keyStore.containsAlias(alias)) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                createKeysJBMR2(context, alias);
            } else {
                createKeysM(alias);
            }
        }
    }

    private static boolean deleteKeys(String alias) throws KeyStoreException {
        KeyStore keyStore = KeyStore.getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
        try {
            keyStore.load(null);
            keyStore.deleteEntry(alias);
            return true;
        } catch (IOException | NoSuchAlgorithmException | CertificateException ex) {
            Timber.w(ex);
            return false;
        }
    }

    public static boolean keyExists(String wallet) throws BrokenPasswordStoreException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStoreHelper.SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
            keyStore.load(null);
            return keyStore.containsAlias(KeyStoreHelper.SecurityConstants.WALLET_PASS_KEY_PREFIX + wallet)
                    || keyStore.containsAlias(WALLET_PASS_KEY_PREFIX_V2 + wallet);
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException ex) {
            throw new BrokenPasswordStoreException(ex);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static void createKeysJBMR2(Context context, String alias) throws NoSuchProviderException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException {

        Calendar start = new GregorianCalendar();
        Calendar end = new GregorianCalendar();
        end.add(Calendar.YEAR, 300);

        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                .setAlias(alias)
                .setSubject(new X500Principal("CN=" + alias))
                .setSerialNumber(BigInteger.valueOf(Math.abs(alias.hashCode())))
                .setStartDate(start.getTime()).setEndDate(end.getTime())
                .build();
        // defaults to 2048 bit modulus
        KeyPairGenerator kpGenerator = KeyPairGenerator.getInstance(
                SecurityConstants.TYPE_RSA,
                SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
        kpGenerator.initialize(spec);
        KeyPair kp = kpGenerator.generateKeyPair();
        Timber.d("preM Keys created");
    }

    @TargetApi(Build.VERSION_CODES.M)
    private static void createKeysM(String alias) throws NoSuchProviderException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
        keyPairGenerator.initialize(
                new KeyGenParameterSpec.Builder(
                        alias, KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setDigests(KeyProperties.DIGEST_SHA1, KeyProperties.DIGEST_SHA256)
                        .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .setUserAuthenticationRequired(alias.startsWith(WALLET_PASS_KEY_PREFIX_V2))
                        .build());
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        Timber.d("M Keys created");
    }

    private static PrivateKey getPrivateKey(String alias) {
        try {
            KeyStore ks = KeyStore
                    .getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
            ks.load(null);
            //KeyStore.Entry entry = ks.getEntry(alias, null);
            PrivateKey privateKey = (PrivateKey) ks.getKey(alias, null);

            if (privateKey == null) {
                Timber.w("No key found under alias: %s", alias);
                return null;
            }

            return privateKey;
        } catch (IOException | NoSuchAlgorithmException | CertificateException
                | UnrecoverableEntryException | KeyStoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static PublicKey getPublicKey(String alias) {
        try {
            KeyStore ks = KeyStore
                    .getInstance(SecurityConstants.KEYSTORE_PROVIDER_ANDROID_KEYSTORE);
            ks.load(null);

            PublicKey publicKey = ks.getCertificate(alias).getPublicKey();

            if (publicKey == null) {
                Timber.w("No public key");
                return null;
            }
            return publicKey;
        } catch (IOException | NoSuchAlgorithmException | CertificateException
                | KeyStoreException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static byte[] encrypt(String alias, byte[] data) {
        try {
            PublicKey publicKey = getPublicKey(alias);
            Cipher cipher = Cipher.getInstance(SecurityConstants.CIPHER_RSA_ECB_OAEP);

            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(data);
        } catch (InvalidKeyException | IllegalBlockSizeException | BadPaddingException
                | NoSuchAlgorithmException | NoSuchPaddingException ex) {
            Timber.e(ex);
            return null;
        }
    }

    /**
     * Signs the data using the key pair stored in the Android Key Store. This
     * signature can be used with the data later to verify it was signed by this
     * application.
     *
     * @return The data signature generated
     */
    private static byte[] signData(String alias, byte[] data) throws NoSuchAlgorithmException,
            InvalidKeyException, SignatureException {
        PrivateKey privateKey = getPrivateKey(alias);
        if (privateKey == null) return null;
        Signature s = Signature.getInstance(SecurityConstants.SIGNATURE_SHA256withRSA);
        s.initSign(privateKey);
        s.update(data);
        return s.sign();
    }

    public interface SecurityConstants {
        String KEYSTORE_PROVIDER_ANDROID_KEYSTORE = "AndroidKeyStore";
        String TYPE_RSA = "RSA";
        String SIGNATURE_SHA256withRSA = "SHA256withRSA";
        String CIPHER_RSA_ECB_OAEP = "RSA/ECB/OAEPWithSHA-1AndMGF1Padding";
        String CIPHER_RSA_ECB_PKCS1 = "RSA/ECB/PKCS1Padding";
        String WALLET_PASS_PREFS_NAME = "wallet";
        String WALLET_PASS_KEY_PREFIX = "walletKey-";
    }
}