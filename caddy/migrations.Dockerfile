FROM flyway/flyway:10.22-alpine

COPY migrations /flyway/sql
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

ENTRYPOINT ["/entrypoint.sh"]
CMD ["migrate"]