FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt piper-tts
COPY bridge.py wake.py .
ENV PORT=8765
EXPOSE 8765
CMD ["python", "bridge.py"]
