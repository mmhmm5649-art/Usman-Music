FROM node:22-alpine

WORKDIR /app
COPY package.json ws-protocol.mjs room-manager.mjs server.mjs ./

ENV NODE_ENV=production
ENV PORT=8080
ENV HOST=0.0.0.0

EXPOSE 8080

USER node
CMD ["node", "server.mjs"]
