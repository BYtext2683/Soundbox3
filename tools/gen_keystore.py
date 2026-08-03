"""生成 SoundBox 的固定签名密钥库（PKCS#12）。

只需运行一次。生成的 keystore/soundbox.p12 会随仓库一起提交，
保证每次 GitHub 云端编译出来的 APK 签名一致，手机上可以直接覆盖升级。

用法：python tools/gen_keystore.py
依赖：pip install cryptography
"""

import datetime
import os

from cryptography import x509
from cryptography.x509.oid import NameOID
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives.serialization import pkcs12, PrivateFormat

PWD = b"soundbox"
ALIAS = b"soundbox"
OUT = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "keystore", "soundbox.p12")


def main() -> None:
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([
        x509.NameAttribute(NameOID.COMMON_NAME, "SoundBox"),
        x509.NameAttribute(NameOID.ORGANIZATIONAL_UNIT_NAME, "Personal"),
        x509.NameAttribute(NameOID.ORGANIZATION_NAME, "SoundBox"),
        x509.NameAttribute(NameOID.COUNTRY_NAME, "CN"),
    ])
    nb = datetime.datetime(2025, 1, 1, tzinfo=datetime.timezone.utc)
    na = datetime.datetime(2055, 1, 1, tzinfo=datetime.timezone.utc)
    cert = (x509.CertificateBuilder()
            .subject_name(name)
            .issuer_name(name)
            .public_key(key.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(nb)
            .not_valid_after(na)
            .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
            .add_extension(x509.SubjectKeyIdentifier.from_public_key(key.public_key()),
                           critical=False)
            .sign(key, hashes.SHA256()))

    enc = (PrivateFormat.PKCS12.encryption_builder()
           .kdf_rounds(50000)
           .key_cert_algorithm(pkcs12.PBES.PBESv2SHA256AndAES256CBC)
           .hmac_hash(hashes.SHA256())
           .build(PWD))

    blob = pkcs12.serialize_key_and_certificates(
        name=ALIAS, key=key, cert=cert, cas=None, encryption_algorithm=enc)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "wb") as f:
        f.write(blob)

    reloaded = pkcs12.load_key_and_certificates(blob, PWD)
    fp = reloaded[1].fingerprint(hashes.SHA256()).hex(":").upper()
    print("keystore ->", OUT)
    print("bytes    =", len(blob))
    print("SHA-256  =", fp)


if __name__ == "__main__":
    main()
