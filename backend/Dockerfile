# 1. Use an official lightweight Python image
FROM python:3.11-slim

# 2. Set the working directory inside the container
WORKDIR /app

# 3. Copy the dependencies file first (for better caching)
COPY requirements.txt .

# 4. Install dependencies
RUN pip install --no-cache-dir -r requirements.txt

# 5. Copy the rest of your application code
COPY . .

# 6. Expose the port Google Cloud Run expects (8080 is default)
EXPOSE 8080

# 7. The command to run your app
# Note: We use 0.0.0.0 so external requests can reach it
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]