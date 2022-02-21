import datetime
from Crypto.PublicKey import ECC
from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.backends import default_backend
from cryptography.hazmat.primitives.serialization import Encoding

private_value = 999
private_key = ec.derive_private_key(private_value, ec.SECP256R1(), default_backend())

def get_certificate(request):
    body = request.get_json()
    certificate = generate_certificate(body["email"], pem_key_to_key_object(body["publicKeyString"]))
    return certificate.public_bytes(Encoding.PEM).decode("utf-8")

def generate_certificate(email, publicKey):
                builder = x509.CertificateBuilder()
                builder = builder.subject_name(x509.Name([x509.NameAttribute(x509.NameOID.COMMON_NAME, email)]))
                builder = builder.issuer_name(x509.Name([x509.NameAttribute(x509.NameOID.COMMON_NAME, "TTM4905 CA")]))
                builder = builder.not_valid_before(datetime.datetime.utcnow())
                builder = builder.not_valid_after(datetime.datetime.utcnow() + datetime.timedelta(days=30))
                builder = builder.serial_number(x509.random_serial_number())
                builder = builder.public_key(publicKey)
                certificate = builder.sign(private_key, hashes.SHA256(), default_backend())
                return certificate

def pem_key_to_key_object(pemKey):
    key = ECC.import_key(pemKey)
    sec1_key = key.public_key().export_key(format='SEC1')
    keyObj = ec.EllipticCurvePublicKey.from_encoded_point(ec.SECP256R1(), sec1_key)
    return keyObj
