
Linux
-----

```bash
keytool -importkeystore -srckeystore visallo-vm.visallo.org.jks -destkeystore visallo-vm.visallo.org.p12 -srcalias visallo-vm.visallo.org -srcstoretype jks -deststoretype pkcs12
# password is password
openssl pkcs12 -in visallo-vm.visallo.org.p12 -out visallo-vm.visallo.org.pem
certutil -d sql:$HOME/.pki/nssdb -A -t "P,," -n visallo-vm.visallo.org -i visallo-vm.visallo.org.pem
```

completely restart Chrome

