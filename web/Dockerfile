
FROM python:3.12-alpine

WORKDIR /app

# Copy the frontend files and Python server
COPY . .

# Run as a non-root user
RUN adduser -D app
USER app

# Frontend port
ENV UI_PORT=3000

EXPOSE 3000

CMD ["python", "server.py"]
