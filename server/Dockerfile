# Node.js 20 LTS Alpine Image
FROM node:20-alpine

# Set working directory
WORKDIR /app

# Copy package files and install dependencies
COPY package*.json ./
RUN npm install --production

# Copy application source code
COPY . .

# Create directories for persistent database and file uploads
RUN mkdir -p /app/data /app/uploads

# Expose server port
EXPOSE 5000

# Environment variables
ENV NODE_ENV=production
ENV PORT=5000
ENV DATA_DIR=/app/data
ENV UPLOAD_DIR=/app/uploads

# Start the 24/7 server
CMD ["node", "index.js"]
