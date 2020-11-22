FROM    alpine:3.12.1 as ngrok

RUN     apk add --no-cache --virtual .bootstrap-deps ca-certificates && \
        wget -O /tmp/ngrok.zip https://bin.equinox.io/c/4VmDzA7iaHb/ngrok-stable-linux-amd64.zip && \
        unzip -o /tmp/ngrok.zip -d / && \
        apk del .bootstrap-deps && \
        rm -rf /tmp/* && \
        rm -rf /var/cache/apk/*

FROM    busybox:1.32.0-glibc

COPY --from=ngrok /ngrok /bin/ngrok
COPY ngrok_discover /bin/ngrok_discover
COPY ngrok.yml /home/ngrok/.ngrok2/ngrok.yml

RUN     addgroup -g 4551 -S ngrok && \
        adduser -u 4551 -S ngrok -G ngrok -h /home/ngrok -s /bin/ash && \
        chown -R ngrok:ngrok /home/ngrok && \
        chmod +x /bin/ngrok_discover

USER    ngrok:ngrok

RUN ngrok --version

EXPOSE 4040

CMD ["/bin/ngrok_discover"]
